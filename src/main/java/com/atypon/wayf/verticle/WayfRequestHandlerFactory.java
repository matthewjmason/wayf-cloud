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

package com.atypon.wayf.verticle;

import com.atypon.wayf.request.RequestContextAccessor;
import com.atypon.wayf.request.RequestContextFactory;
import com.atypon.wayf.request.ResponseWriter;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;

/**
 * An implementation of Handler that ensures all inbound requests are handled uniformly. This stores relevant
 * information about inbound requests, switches the processing to an appropriate threadpool, and guarantees consistent
 * response marshalling for both successes and failures.
 */
@Singleton
public class WayfRequestHandlerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(WayfRequestHandlerFactory.class);

    @Inject
    private RequestContextFactory requestContextFactory;

    public WayfRequestHandlerFactory() {
        Json.prettyMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S"));
    }

    public Handler<RoutingContext> observable(Function<RoutingContext, Observable<?>> delegate) {
        return new WayfRequestHandlerObservableImpl(requestContextFactory, delegate);
    }

    public Handler<RoutingContext> single(Function<RoutingContext, Single<?>> delegate) {
        return new WayfRequestHandlerSingleImpl(requestContextFactory, delegate);
    }

    public Handler<RoutingContext> completable(Function<RoutingContext, Completable> delegate) {
        return new WayfRequestHandlerCompletableImpl(requestContextFactory, delegate);
    }

    private static class WayfRequestHandlerSingleImpl<T> implements Handler<RoutingContext> {
        private Function<RoutingContext, Single<T>> singleDelegate;
        private RequestContextFactory requestContextFactory;

        public WayfRequestHandlerSingleImpl(RequestContextFactory requestContextFactory, Function<RoutingContext, Single<T>> delegate) {
            this.requestContextFactory = requestContextFactory;
            this.singleDelegate = delegate;
        }

        public void handle(RoutingContext event) {
            RequestContextAccessor.set(requestContextFactory.fromRoutingContext(event));

            Single.just(event)
                    .observeOn(Schedulers.io())
                    .flatMap((s_event) -> singleDelegate.apply(s_event))
                    .subscribeOn(Schedulers.io()) // Write HTTP response on IO thread
                    .subscribe(
                            (result) -> ResponseWriter.buildSuccess(event, result),
                            (e) -> event.fail(e)
                    );

            RequestContextAccessor.remove();
        }
    }

    private static class WayfRequestHandlerObservableImpl<T> implements Handler<RoutingContext> {
        private RequestContextFactory requestContextFactory;
        private Function<RoutingContext, Observable<T>> observableDelegate;

        public WayfRequestHandlerObservableImpl(RequestContextFactory requestContextFactory, Function<RoutingContext, Observable<T>> delegate) {
            this.requestContextFactory = requestContextFactory;
            this.observableDelegate = delegate;
        }

        public void handle(RoutingContext event) {
            RequestContextAccessor.set(requestContextFactory.fromRoutingContext(event));

            Single.just(event)
                    .observeOn(Schedulers.io())
                    .flatMapObservable((s_event) -> observableDelegate.apply(s_event))
                    .toList()
                    .subscribeOn(Schedulers.io()) // Write HTTP response on IO thread
                    .subscribe(
                            (result) -> ResponseWriter.buildSuccess(event, result),
                            (e) -> event.fail(e)
                    );

            RequestContextAccessor.remove();
        }
    }

    private static class WayfRequestHandlerCompletableImpl implements Handler<RoutingContext> {
        private RequestContextFactory requestContextFactory;
        private Function<RoutingContext, Completable> completableDelgate;

        public WayfRequestHandlerCompletableImpl(RequestContextFactory requestContextFactory, Function<RoutingContext, Completable> delegate) {
            this.requestContextFactory = requestContextFactory;
            this.completableDelgate = delegate;
        }

        public void handle(RoutingContext event) {
            RequestContextAccessor.set(requestContextFactory.fromRoutingContext(event));

            Single.just(event)
                    .observeOn(Schedulers.io())
                    .flatMapCompletable((s_event) -> completableDelgate.apply(s_event))
                    .subscribeOn(Schedulers.io()) // Write HTTP response on IO thread
                    .subscribe(
                            () -> ResponseWriter.buildSuccess(event, null),
                            (e) -> event.fail(e)
                    );

            RequestContextAccessor.remove();
        }
    }
}