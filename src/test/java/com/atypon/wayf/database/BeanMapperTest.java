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

package com.atypon.wayf.database;


import com.atypon.wayf.dao.impl.AuthenticationDaoDbImpl;
import com.atypon.wayf.data.Authenticatable;
import com.atypon.wayf.data.device.access.DeviceAccess;
import com.atypon.wayf.data.publisher.Publisher;
import com.atypon.wayf.guice.WayfGuiceModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class BeanMapperTest {

    @Inject
    private NestedFieldBeanMapper beanMapper;

    @Before
    public void setUp() {
        Guice.createInjector(new WayfGuiceModule()).injectMembers(this);
    }

    @Test
    public void testMapNested() throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("device.globalId", "testDeviceId");

        DeviceAccess deviceAccess = beanMapper.map(row, DeviceAccess.class);

        assertEquals("testDeviceId", deviceAccess.getDevice().getGlobalId());
    }

    @Test
    public void testBeanFactory() {
        Map<String, Object> row = new HashMap<>();
        row.put(AuthenticationDaoDbImpl.AUTHENTICATABLE_TYPE, Authenticatable.Type.PUBLISHER.toString());
        row.put(AuthenticationDaoDbImpl.AUTHENTICATABLE_ID, 123L);

        Authenticatable authenticatable = beanMapper.map(row, Authenticatable.class);
        assertNotNull(authenticatable);
        assertEquals(Publisher.class, authenticatable.getClass());
        assertEquals(new Long(123L), authenticatable.getId());
    }
}
