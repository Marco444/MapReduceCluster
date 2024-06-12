package ar.edu.itba.pod.tpe2.client.query5;

import ar.edu.itba.pod.Util;
import ar.edu.itba.pod.data.Ticket;
import ar.edu.itba.pod.query5.InfractionsToAverageMapper;
import ar.edu.itba.pod.query5.InfractionsToAverageReducer;
import ar.edu.itba.pod.query5.groupByAverageMapper;
import ar.edu.itba.pod.query5.groupByAverageReducer;
import ar.edu.itba.pod.tpe2.client.QueryClient;
import com.hazelcast.core.MultiMap;
import com.hazelcast.mapreduce.Job;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.KeyValueSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SameAverageValuePairs extends QueryClient {

    public SameAverageValuePairs() {
        super();
        execute();
    }

    @Override
    public void resolveQuery() throws ExecutionException, InterruptedException, IOException {

        // 1st Map-Reduce
        final JobTracker jobTracker = getHz().getJobTracker(Util.HAZELCAST_NAMESPACE);
        final KeyValueSource<String, Ticket> source = KeyValueSource.fromMultiMap(getHz().getMultiMap(Util.HAZELCAST_NAMESPACE));
        Job<String, Ticket> infractionsAverageJob = jobTracker.newJob(source);

        Map<String, Double> infractionsAverage = infractionsAverageJob
                .mapper(new InfractionsToAverageMapper())
                .reducer(new InfractionsToAverageReducer())
                .submit()
                .get();
        System.out.println(infractionsAverage);

        loadAuxData(infractionsAverage);
        // 2nd Map-Reduce
        final JobTracker groupByAverageJobTracker = getHz().getJobTracker(Util.HAZELCAST_NAMESPACE_QUERY_5);
        final KeyValueSource<String, Double> groupByAverageJobSource = KeyValueSource.fromMap(getHz().getMap(Util.HAZELCAST_NAMESPACE_QUERY_5));
        Job<String, Double> groupByAverageJob = groupByAverageJobTracker.newJob(groupByAverageJobSource);
        Map<Integer, List<String>> groupByAverage = groupByAverageJob
                .mapper(new groupByAverageMapper())
                //.combiner()
                .reducer(new groupByAverageReducer())
                .submit()
                .get();
        System.out.println(groupByAverage);
    }

    private void loadAuxData(Map<String, Double> infractionsAverage) throws ExecutionException, InterruptedException {
        getHz().getMap(Util.HAZELCAST_NAMESPACE_QUERY_5).putAll(infractionsAverage);
    }

    @Override
    public String getQueryNumber() {
        return "5";
    }

    @Override
    public String getQueryHeader() {
        return "Group" + Util.CSV_DELIMITER + "Infraction A" + Util.CSV_DELIMITER + "Infraction B";
    }

    public static void main(String[] args) {
        QueryClient client = new SameAverageValuePairs();
    }
}
