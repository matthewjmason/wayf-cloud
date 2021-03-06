/*
 * Copyright 2017 Atypon Systems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atypon.wayf.facade.impl;

import com.atypon.wayf.cache.Cache;
import com.atypon.wayf.dao.PublisherDao;
import com.atypon.wayf.data.publisher.Publisher;
import com.atypon.wayf.data.publisher.PublisherQuery;
import com.atypon.wayf.data.publisher.PublisherStatus;
import com.atypon.wayf.facade.AuthenticationFacade;
import com.atypon.wayf.facade.PublisherFacade;
import com.atypon.wayf.facade.ClientJsFacade;
import com.atypon.wayf.reactivex.FacadePolicies;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.apache.http.HttpStatus;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

import static com.atypon.wayf.reactivex.FacadePolicies.singleOrException;

@Singleton
public class PublisherFacadeImpl implements PublisherFacade {

    @Inject
    private PublisherDao publisherDao;

    @Inject
    private AuthenticationFacade authenticationFacade;

    @Inject
    private ClientJsFacade clientJsFacade;

    @Inject
    @Named("publisherSaltCache")
    private Cache<Long, String> saltCache;

    public PublisherFacadeImpl() {
    }

    @Override
    public Single<Publisher> create(Publisher publisher) {
        publisher.setStatus(PublisherStatus.ACTIVE);
        publisher.setSalt(generateSalt());

        return publisherDao.create(publisher) // Create the publisher
                .flatMap((createdPublisher) ->

                        Single.zip(
                                // Create an authorization token for the newly created publisher
                                authenticationFacade.createToken(createdPublisher).compose(single -> FacadePolicies.applySingle(single)),

                                // Generate the publisher specific Javascript widget
                                clientJsFacade.generateWidgetForPublisher(createdPublisher).compose(single -> FacadePolicies.applySingle(single)),

                                // Combine the results with the previously created publisher
                                (token, filename) -> {
                                    createdPublisher.setToken(token);
                                    createdPublisher.setWidgetLocation(filename);
                                    return createdPublisher;
                                }
                        )
                );
    }

    @Override
    public Single<Publisher> read(Long id) {
        return singleOrException(publisherDao.read(id), HttpStatus.SC_NOT_FOUND, "Invalid Publisher ID");
    }

    @Override
    public Observable<Publisher> filter(PublisherQuery filter) {
        return publisherDao.filter(filter);
    }

    @Override
    public Single<Publisher> lookupCode(String publisherCode) {
        PublisherQuery query = new PublisherQuery().setCodes(Lists.newArrayList(publisherCode));

        return singleOrException(filter(query), HttpStatus.SC_BAD_REQUEST, "Could not find publisher for code [{}]", publisherCode);
    }

    private String generateSalt() {
        SecureRandom random = new SecureRandom();

        byte bytes[] = new byte[20];
        random.nextBytes(bytes);

        return BCrypt.gensalt(10, random);
    }

    @Override
    public String getPublishersSalt(Long publisherId) {
        return singleOrException(saltCache.get(publisherId), HttpStatus.SC_INTERNAL_SERVER_ERROR, "Could not find Publisher encryption salt for id [{}]", publisherId).blockingGet();
    }
}
