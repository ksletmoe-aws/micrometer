/*
 * Copyright 2017 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.cloudwatchembeddedmetrics;

import io.micrometer.common.lang.Nullable;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;

import java.time.Duration;

class CloudWatchEmbeddedMetricsMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {

    private final CloudWatchEmbeddedMetricsConfig config = new CloudWatchEmbeddedMetricsConfig() {
        @Override
        @Nullable
        public String get(String key) {
            return null;
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };

    @Override
    public MeterRegistry registry() {
        // noinspection ConstantConditions
        return new CloudWatchEmbeddedMetricsMeterRegistry(config, new MockClock());
    }

    @Override
    public Duration step() {
        return config.step();
    }

}