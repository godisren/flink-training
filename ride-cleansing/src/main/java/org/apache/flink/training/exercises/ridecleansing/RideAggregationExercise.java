/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.training.exercises.ridecleansing;

import org.apache.flink.api.common.JobExecutionResult;
import org.apache.flink.api.common.functions.FilterFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.training.exercises.common.datatypes.TaxiRide;
import org.apache.flink.training.exercises.common.sources.TaxiRideGenerator;
import org.apache.flink.training.exercises.common.utils.GeoUtils;
import org.apache.flink.util.Collector;

/**
 * The Ride Cleansing exercise from the Flink training.
 *
 * <p>The task of this exercise is to filter a data stream of taxi ride records to keep only rides
 * that both start and end within New York City. The resulting stream should be printed.
 */
public class RideAggregationExercise {

    private final SourceFunction<TaxiRide> source;
    private final SinkFunction<TaxiRide> sink;

    /** Creates a job using the source and sink provided. */
    public RideAggregationExercise(SourceFunction<TaxiRide> source, SinkFunction<TaxiRide> sink) {

        this.source = source;
        this.sink = sink;
    }

    /**
     * Main method.
     *
     * @throws Exception which occurs during job execution.
     */
    public static void main(String[] args) throws Exception {
        RideAggregationExercise job =
                new RideAggregationExercise(new TaxiRideGenerator(), new PrintSinkFunction<>());

        job.execute();
    }

    /**
     * Creates and executes the long rides pipeline.
     *
     * @return {JobExecutionResult}
     * @throws Exception which occurs during job execution.
     */
    public JobExecutionResult execute() throws Exception {

        // set up streaming execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // set up the pipeline
        env.addSource(source)
                .filter(new NYCFilter())
                .keyBy(e -> e.rideId)// key by ride ID
                .flatMap(new AggregateRideDuration())
                .print();

        // run the pipeline and return the result
        return env.execute("Taxi Ride Cleansing");
    }

    public static class AggregateRideDuration extends RichFlatMapFunction<TaxiRide, Tuple2<Long, Double>> {

        private ValueState<Long> startTimeState;

        @Override
        public void open(Configuration parameters) throws Exception {
            startTimeState = this.getRuntimeContext().getState(new ValueStateDescriptor<>("startTimeState", Long.class)) ;
        }

        @Override
        public void flatMap(TaxiRide ride, Collector<Tuple2<Long, Double>> out) throws Exception {

            if (ride.isStart) {
                startTimeState.update(ride.getEventTimeMillis());
            } else {
                Long startTime = startTimeState.value();
                if (startTime!=null) {
                    Long endTime = ride.getEventTimeMillis();
                    double duration = (endTime - startTime ) /1000.0;

                    out.collect(Tuple2.of(ride.rideId, duration));

                    startTimeState.clear();
                }
            }
        }
    }

    /** Keep only those rides and both start and end in NYC. */
    public static class NYCFilter implements FilterFunction<TaxiRide> {
        @Override
        public boolean filter(TaxiRide taxiRide) throws Exception {
            // throw new MissingSolutionException();
            return GeoUtils.isInNYC(taxiRide.startLon, taxiRide.startLat) &&
                    GeoUtils.isInNYC(taxiRide.endLon, taxiRide.endLat);
        }
    }
}
