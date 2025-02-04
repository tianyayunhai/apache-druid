/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.guice;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import org.apache.druid.discovery.DataNodeService;
import org.apache.druid.error.ExceptionMatcher;
import org.apache.druid.guice.annotations.Self;
import org.apache.druid.initialization.Initialization;
import org.apache.druid.query.DruidProcessingConfig;
import org.apache.druid.segment.loading.SegmentLoaderConfig;
import org.apache.druid.segment.loading.StorageLocationConfig;
import org.apache.druid.server.DruidNode;
import org.apache.druid.server.coordination.DruidServerMetadata;
import org.apache.druid.server.coordination.ServerType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

@RunWith(MockitoJUnitRunner.class)
public class StorageNodeModuleTest
{
  private static final boolean INJECT_SERVER_TYPE_CONFIG = true;

  private DruidNode self;
  private ServerTypeConfig serverTypeConfig;
  @Mock
  private DruidProcessingConfig druidProcessingConfig;
  @Mock
  private SegmentLoaderConfig segmentLoaderConfig;
  @Mock
  private StorageLocationConfig storageLocation;

  private StorageNodeModule target;

  @Before
  public void setUp()
  {
    self = new DruidNode("test", "test-host", true, 80, 443, false, true);
    serverTypeConfig = new ServerTypeConfig(ServerType.HISTORICAL);

    Mockito.when(segmentLoaderConfig.getLocations()).thenReturn(Collections.singletonList(storageLocation));

    target = new StorageNodeModule();
  }

  @Test
  public void testIsSegmentCacheConfiguredIsInjected()
  {
    Boolean isSegmentCacheConfigured = injector().getInstance(
        Key.get(Boolean.class, Names.named(StorageNodeModule.IS_SEGMENT_CACHE_CONFIGURED))
    );
    Assert.assertNotNull(isSegmentCacheConfigured);
    Assert.assertTrue(isSegmentCacheConfigured);
  }

  @Test
  public void testIsSegmentCacheConfiguredWithNoLocationsConfiguredIsInjected()
  {
    mockSegmentCacheNotConfigured();
    Boolean isSegmentCacheConfigured = injector().getInstance(
        Key.get(Boolean.class, Names.named(StorageNodeModule.IS_SEGMENT_CACHE_CONFIGURED))
    );
    Assert.assertNotNull(isSegmentCacheConfigured);
    Assert.assertFalse(isSegmentCacheConfigured);
  }

  @Test
  public void getDataNodeServiceWithNoServerTypeConfigShouldThrowProvisionException()
  {
    Injector injector = makeInjector(!INJECT_SERVER_TYPE_CONFIG);

    ExceptionMatcher
        .of(ProvisionException.class)
        .expectMessageContains("Must override the binding for ServerTypeConfig if you want a DataNodeService.")
        .assertThrowsAndMatches(() -> injector.getInstance(DataNodeService.class));
  }

  @Test
  public void getDataNodeServiceWithNoSegmentCacheConfiguredThrowProvisionException()
  {
    mockSegmentCacheNotConfigured();

    ExceptionMatcher
        .of(ProvisionException.class)
        .expectMessageContains("druid.segmentCache.locations must be set on historicals.")
        .assertThrowsAndMatches(() -> injector().getInstance(DataNodeService.class));
  }

  @Test
  public void getDataNodeServiceIsInjectedAsSingleton()
  {
    final Injector injector = injector();

    DataNodeService dataNodeService = injector.getInstance(DataNodeService.class);
    Assert.assertNotNull(dataNodeService);

    DataNodeService other = injector.getInstance(DataNodeService.class);
    Assert.assertSame(dataNodeService, other);
  }

  @Test
  public void getDataNodeServiceIsInjectedAndDiscoverable()
  {
    DataNodeService dataNodeService = injector().getInstance(DataNodeService.class);
    Assert.assertNotNull(dataNodeService);
    Assert.assertTrue(dataNodeService.isDiscoverable());
  }

  @Test
  public void getDataNodeServiceWithSegmentCacheNotConfiguredIsInjectedAndDiscoverable()
  {
    mockSegmentCacheNotConfigured();
    serverTypeConfig = new ServerTypeConfig(ServerType.BROKER);
    DataNodeService dataNodeService = injector().getInstance(DataNodeService.class);
    Assert.assertNotNull(dataNodeService);
    Assert.assertFalse(dataNodeService.isDiscoverable());
  }

  @Test
  public void testDruidServerMetadataIsInjectedAsSingleton()
  {
    final Injector injector = injector();
    DruidServerMetadata druidServerMetadata = injector.getInstance(DruidServerMetadata.class);
    Assert.assertNotNull(druidServerMetadata);

    DruidServerMetadata other = injector.getInstance(DruidServerMetadata.class);
    Assert.assertSame(druidServerMetadata, other);
  }

  @Test
  public void testDruidServerMetadataWithNoServerTypeConfigShouldThrowProvisionException()
  {
    Injector injector = makeInjector(!INJECT_SERVER_TYPE_CONFIG);

    ExceptionMatcher
        .of(ProvisionException.class)
        .expectMessageContains("Must override the binding for ServerTypeConfig if you want a DruidServerMetadata.")
        .assertThrowsAndMatches(() -> injector.getInstance(DruidServerMetadata.class));
  }

  private Injector injector()
  {
    return makeInjector(INJECT_SERVER_TYPE_CONFIG);
  }

  private Injector makeInjector(boolean withServerTypeConfig)
  {
    return Initialization.makeInjectorWithModules(
        GuiceInjectors.makeStartupInjector(), (ImmutableList.of(Modules.override(
            (binder) -> {
              binder.bind(DruidNode.class).annotatedWith(Self.class).toInstance(self);
              binder.bindConstant().annotatedWith(Names.named("serviceName")).to("test");
              binder.bindConstant().annotatedWith(Names.named("servicePort")).to(0);
              binder.bindConstant().annotatedWith(Names.named("tlsServicePort")).to(-1);
              binder.bind(DruidProcessingConfig.class).toInstance(druidProcessingConfig);
            },
            target).with(
                (binder) -> {
                  binder.bind(SegmentLoaderConfig.class).toInstance(segmentLoaderConfig);
                  if (withServerTypeConfig) {
                    binder.bind(ServerTypeConfig.class).toInstance(serverTypeConfig);
                  }
                }
                )
        )));
  }

  private void mockSegmentCacheNotConfigured()
  {
    Mockito.doReturn(Collections.emptyList()).when(segmentLoaderConfig).getLocations();
  }
}
