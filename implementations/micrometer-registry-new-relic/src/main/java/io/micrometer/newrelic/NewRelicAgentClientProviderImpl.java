/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.newrelic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.newrelic.api.agent.Agent;
import com.newrelic.api.agent.NewRelic;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringUtils;

/**
 * Publishes metrics to New Relic Insights via Java Agent API.
 *
 * @author Neil Powell
 */
public class NewRelicAgentClientProviderImpl implements NewRelicClientProvider {

    private final Logger logger = LoggerFactory.getLogger(NewRelicAgentClientProviderImpl.class);
    
    private final Agent newRelicAgent;
    private final NewRelicConfig config;
    private final NamingConvention namingConvention;
    
    public NewRelicAgentClientProviderImpl(NewRelicConfig config) {
        this(config, NewRelic.getAgent(), new NewRelicNamingConvention());
    }

    public NewRelicAgentClientProviderImpl(NewRelicConfig config, Agent newRelicAgent, NamingConvention namingConvention) {

        if (config.meterNameEventTypeEnabled() == false
                && StringUtils.isEmpty(config.eventType())) {
            throw new MissingRequiredConfigurationException("eventType must be set to report metrics to New Relic");
        }

        this.newRelicAgent = newRelicAgent;
        this.config = config;
        this.namingConvention = namingConvention;
    }

    @Override
    public void publish(NewRelicMeterRegistry meterRegistry) {
        // New Relic's Java Agent Insights API is backed by a reservoir/buffer
        // and handles the actual publishing of events to New Relic.
        // 1:1 mapping between Micrometer meters and New Relic events
        for (Meter meter : meterRegistry.getMeters()) {
            sendEvents(
                    meter.getId(), 
                        meter.match(
                            this::writeGauge,
                            this::writeCounter,
                            this::writeTimer,
                            this::writeSummary,
                            this::writeLongTaskTimer,
                            this::writeTimeGauge,
                            this::writeFunctionCounter,
                            this::writeFunctionTimer,
                            this::writeMeter)
                    );
        }
    }

    @Override
    public Map<String, Object> writeLongTaskTimer(LongTaskTimer timer) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        TimeUnit timeUnit = TimeUnit.valueOf(timer.getId().getBaseUnit());
        addAttribute(ACTIVE_TASKS, timer.activeTasks(), attributes);          	
        addAttribute(DURATION, timer.duration(timeUnit), attributes);
        addAttribute(TIME_UNIT, timeUnit.toString().toLowerCase(), attributes);
        //process meter's name, type and tags
        addMeterAsAttributes(timer.getId(), attributes);
        return attributes;
    }
    
    @Override
    public Map<String, Object> writeFunctionCounter(FunctionCounter counter) {
        return writeCounterValues(counter.getId(), counter.count());
    }

    @Override
    public Map<String, Object> writeCounter(Counter counter) {
        return writeCounterValues(counter.getId(), counter.count());
    }
    
    Map<String, Object> writeCounterValues(Meter.Id id, double count) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        if (Double.isFinite(count)) {
            addAttribute(THROUGHPUT, count, attributes);
            //process meter's name, type and tags
            addMeterAsAttributes(id, attributes);			
        }
        return attributes;
    }

    @Override
    public Map<String, Object> writeGauge(Gauge gauge) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        double value = gauge.value();
        if (Double.isFinite(value)) {
            addAttribute(VALUE, value, attributes);
            //process meter's name, type and tags
            addMeterAsAttributes(gauge.getId(), attributes);			 
        }
        return attributes;
    }

    @Override
    public Map<String, Object> writeTimeGauge(TimeGauge gauge) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        double value = gauge.value();
        if (Double.isFinite(value)) {
            addAttribute(VALUE, value, attributes);
            addAttribute(TIME_UNIT, gauge.baseTimeUnit().toString().toLowerCase(), attributes);
            //process meter's name, type and tags
            addMeterAsAttributes(gauge.getId(), attributes);
        }
        return attributes;
    }
    
    @Override
    public Map<String, Object> writeSummary(DistributionSummary summary) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        addAttribute(COUNT, summary.count(), attributes);          	
        addAttribute(AVG, summary.mean(), attributes);            		
        addAttribute(TOTAL, summary.totalAmount(), attributes);
        addAttribute(MAX, summary.max(), attributes);
        //process meter's name, type and tags
        addMeterAsAttributes(summary.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeTimer(Timer timer) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        TimeUnit timeUnit = TimeUnit.valueOf(timer.getId().getBaseUnit());
        addAttribute(COUNT, (new Double(timer.count())).longValue(), attributes);
        addAttribute(AVG, timer.mean(timeUnit), attributes);
        addAttribute(TOTAL_TIME, timer.totalTime(timeUnit), attributes);		
        addAttribute(MAX, timer.max(timeUnit), attributes);
        addAttribute(TIME_UNIT, timeUnit.toString().toLowerCase(), attributes);
        //process meter's name, type and tags
        addMeterAsAttributes(timer.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeFunctionTimer(FunctionTimer timer) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        TimeUnit timeUnit = TimeUnit.valueOf(timer.getId().getBaseUnit());
        addAttribute(COUNT, (new Double(timer.count())).longValue(), attributes);
        addAttribute(AVG, timer.mean(timeUnit), attributes);
        addAttribute(TOTAL_TIME, timer.totalTime(timeUnit), attributes);
        addAttribute(TIME_UNIT, timeUnit.toString().toLowerCase(), attributes);
        //process meter's name, type and tags
        addMeterAsAttributes(timer.getId(), attributes);
        return attributes;
    }

    @Override
    public Map<String, Object> writeMeter(Meter meter) {
        Map<String, Object> attributes = new HashMap<String, Object>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            addAttribute(measurement.getStatistic().getTagValueRepresentation(), value, attributes);	
        }
        if (attributes.isEmpty()) {
            return attributes;
        }
        //process meter's name, type and tags
        addMeterAsAttributes(meter.getId(), attributes);	
        return attributes;		
    }

    void addMeterAsAttributes(Meter.Id id, Map<String, Object> attributes) {
        if (!config.meterNameEventTypeEnabled()) {
            // Include contextual attributes when publishing all metrics under a single categorical eventType,
            // NOT when publishing an eventType per Meter/metric name
            String name = id.getConventionName(namingConvention);
            attributes.put(METRIC_NAME, name);
            attributes.put(METRIC_TYPE, id.getType().toString());
        }
        //process meter tags
        for (Tag tag : id.getConventionTags(namingConvention)) {
            attributes.put(tag.getKey(), tag.getValue());
        }
    }

    void addAttribute(String key, Number value, Map<String, Object> attributes) {
        //process other tags
        
        //Replicate DoubleFormat.wholeOrDecimal(value.doubleValue()) formatting behavior
        if (Math.floor(value.doubleValue()) == value.doubleValue()) {
            //whole number - don't include decimal
            attributes.put(namingConvention.tagKey(key), value.intValue());
        } else {
            //include decimal
            attributes.put(namingConvention.tagKey(key), value.doubleValue());
        }
    }
    
    void addAttribute(String key, String value, Map<String, Object> attributes) {
        //process other tags
        attributes.put(namingConvention.tagKey(key), namingConvention.tagValue(value));
    }

    void sendEvents(Meter.Id id, Map<String, Object> attributes) {
        //Delegate to New Relic Java Agent
        if (attributes != null && attributes.isEmpty() == false) {
            String eventType = getEventType(id, config, namingConvention);
            try {
                newRelicAgent.getInsights().recordCustomEvent(eventType, attributes);
            } catch (Throwable e) {
                logger.warn("failed to send metrics to new relic", e);
            }
        }
    }
}