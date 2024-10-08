package ar.edu.itba.pod.tpe2.client.query5;

import ar.edu.itba.pod.Constants;
import ar.edu.itba.pod.data.Ticket;
import ar.edu.itba.pod.query5.InfractionsToAverageMapper;
import ar.edu.itba.pod.query5.InfractionsToAverageReducer;
import ar.edu.itba.pod.query5.groupByAverageMapper;
import ar.edu.itba.pod.query5.groupByAverageReducer;
import ar.edu.itba.pod.tpe2.client.QueryClient;
import com.hazelcast.mapreduce.Job;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.KeyValueSource;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class SameAverageValuePairs extends QueryClient {

    public SameAverageValuePairs() {
        super();
        execute();
    }

    @Override
    public void resolveQuery() throws ExecutionException, InterruptedException, IOException {

        getFileLogger().info("Inicio del trabajo map/reduce (completo)");

        // 1st Map-Reduce
        getFileLogger().info("Inicio del 1er trabajo map/reduce");
        final JobTracker jobTracker = getHz().getJobTracker(Constants.HAZELCAST_NAMESPACE);
        final KeyValueSource<String, Ticket> source = KeyValueSource.fromMultiMap(getHz().getMultiMap(Constants.HAZELCAST_NAMESPACE));
        Job<String, Ticket> infractionsAverageJob = jobTracker.newJob(source);

        Map<String, Double> infractionsAverage = infractionsAverageJob
                .mapper(new InfractionsToAverageMapper())
                .reducer(new InfractionsToAverageReducer())
                .submit()
                .get();
        getFileLogger().info("Fin del 1er trabajo map/reduce");

        loadAuxData(infractionsAverage);
        // 2nd Map-Reduce
        getFileLogger().info("Inicio del 2do trabajo map/reduce");
        final JobTracker groupByAverageJobTracker = getHz().getJobTracker(Constants.HAZELCAST_NAMESPACE_QUERY_5);
        final KeyValueSource<String, Double> groupByAverageJobSource = KeyValueSource.fromMap(getHz().getMap(Constants.HAZELCAST_NAMESPACE_QUERY_5));
        Job<String, Double> groupByAverageJob = groupByAverageJobTracker.newJob(groupByAverageJobSource);
        Map<Integer, List<String>> groupByAverage = groupByAverageJob
                .mapper(new groupByAverageMapper())
                //.combiner()
                .reducer(new groupByAverageReducer())
                .submit()
                .get();
        getFileLogger().info("Fin del 2do trabajo map/reduce");

        Set<SameAverageValuePairsResults> results = formatResults(groupByAverage);
        writeResults(results);

        getFileLogger().info("Inicio del trabajo map/reduce (completo)");
    }

    private Set<SameAverageValuePairsResults> formatResults(Map<Integer, List<String>> groupByAverage){
        Set<SameAverageValuePairsResults> results = new TreeSet<>();
        for(Integer key : groupByAverage.keySet()){
            List<String> values = groupByAverage.get(key);
            for(int i = 0; i < values.size(); i++){
                for(int j = i + 1; j < values.size(); j++){
                    results.add(new SameAverageValuePairsResults(key.toString(), values.get(i), values.get(j)));
                }
            }
        }
        return results;
    }

    private void loadAuxData(Map<String, Double> infractionsAverage) throws ExecutionException, InterruptedException {
        getHz().getMap(Constants.HAZELCAST_NAMESPACE_QUERY_5).putAll(infractionsAverage);
    }

    @Override
    public String getQueryNumber() {
        return "5";
    }

    @Override
    public String getQueryHeader() {
        return "Group" + Constants.CSV_DELIMITER + "Infraction A" + Constants.CSV_DELIMITER + "Infraction B";
    }

    public static void main(String[] args) {
        QueryClient client = new SameAverageValuePairs();
    }
}
