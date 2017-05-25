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

package com.atypon.wayf.dao.impl;

import com.atypon.wayf.dao.AuthenticationDao;
import com.atypon.wayf.data.Authenticatable;
import com.atypon.wayf.data.publisher.Publisher;
import com.atypon.wayf.data.user.User;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Singleton
public class AuthenticationDaoRedisImpl implements AuthenticationDao {
    private static final String DEFAULT_PREFIX = "AUTHENTICATION_";

    @Inject
    @Named("authenticationDaoDbImpl")
    private AuthenticationDao dbDao;

    @Inject
    private JedisPool pool;

    private String prefix = DEFAULT_PREFIX;

    public void setDbDao(AuthenticationDao dbDao) {
        this.dbDao = dbDao;
    }

    @Override
    public Completable create(String token, Authenticatable authenticatable) {
        throw new UnsupportedOperationException("Cannot create via Redis implementation");
    }

    @Override
    public Maybe<Authenticatable> authenticate(String token) {
        String value = null;

        // Try to read the value from Redis
        try (Jedis jedis = pool.getResource()) {
            value = jedis.get(buildKey(token));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // If nothing was found in Redis, check the database. If a value is found, store it in Redis and return
        // If something was found in Redis, transform it to a POJO
        if (value == null) {
            return dbDao.authenticate(token)
                    .map((authenticatable) -> {
                        String readValue = serialize(authenticatable);

                        try (Jedis jedis = pool.getResource()) {
                            jedis.set(buildKey(token), readValue);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }

                        return authenticatable;
                    });
        } else {
            try {
                Authenticatable authenticatable = deserialize(value);

                return Maybe.just(authenticatable);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private String serialize(Authenticatable authenticatable) {
        JSONObject object = new JSONObject();
        object.put("type", authenticatable.getType());
        object.put("id", authenticatable.getId());

        return object.toString();
    }

    private Authenticatable deserialize(String json) {
        JSONObject object = new JSONObject(json);

        Authenticatable.Type type = object.getEnum(Authenticatable.Type.class, "type");

        Authenticatable authenticatable = null;
        if (Authenticatable.Type.PUBLISHER == type) {
            authenticatable = new Publisher();
        }

        authenticatable.setId(object.getLong("id"));

        return authenticatable;
    }

    private String buildKey(String token) {
        return prefix + token;
    }
}
