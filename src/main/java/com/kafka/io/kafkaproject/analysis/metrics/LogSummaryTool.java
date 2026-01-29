package com.kafka.io.kafkaproject.analysis.metrics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * results/{scenarioId}/*.log 를 읽어 stage별 latency(p95/p99/max)를 summary.csv로 저장
 *
 * 로그 포맷:
 * ts|thread|clientId|stage|requestId|latency_ms
 *
 * 출력 CSV(ROW 방식):
 * scenario,clientId,stage,samples,p95_ms,p99_ms,max_ms
 *
 * 사용 예시(인텔리J에서 main 실행 + args):
 *   S0 ack_received --warmupSec 5
 *   S1 ack_received --warmupSec 5
 *   S0 fetch_received process_done poll_timeout --warmupSec 5
 */
public class LogSummaryTool {
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        if(args.length < 2) {
            System.out.println("""
                    Usage:
                      <scenarioId> <stage1> [stage2 ...] [--warmupSec N] [--out summary.csv]

                    Example:
                      S0 ack_received --warmupSec 5
                      S1 ack_received --warmupSec 5
                    """);
            return;
        }
        String scenarioId = args[0];

        // stages: args에서 --옵션 전까지 모두 stage로 간주
        List<String> stages=new ArrayList<>();
        int i=1;
        while(i<args.length&&!args[i].startsWith("--")) {
            stages.add(args[i]);
            i++;
        }

        int warmupSec=0;
        String outName="summary.csv";

        while(i<args.length) {
            String opt=args[i];
            if("--warmupSec".equals(opt)) {
                warmupSec=Integer.parseInt(args[i+1]);
                i+=2;
            }else if("--out".equals(opt)) {
                outName=args[i+1];
                i+=2;
            }else{
                throw new IllegalArgumentException("Unknown option: "+opt);
            }
        }

        Path scenarioDir= Paths.get("results", scenarioId);
        if(!Files.isDirectory(scenarioDir)) {
            throw new FileNotFoundException("scenario directory not found: "+scenarioDir);
        }

        Map<String, Map<String, List<Double>>> data= new LinkedHashMap<>();

        try(DirectoryStream<Path> stream = Files.newDirectoryStream(scenarioDir, "*.log")) {
            for(Path logFile: stream) {
                readOneLogFile(logFile, stages, warmupSec, data);
            }
        }
        Path out=scenarioDir.resolve(outName);
        writeSummaryCsv(out, scenarioId, data, stages);

        System.out.println("Wrote: "+out.toAbsolutePath());
    }
    private static void readOneLogFile(
            Path logFile,
            List<String> stagesFilter,
            int warmupSec,
            Map<String, Map<String, List<Double>>> out
    )throws IOException {
        Instant firstTs=null;

        try(BufferedReader br=Files.newBufferedReader(logFile)) {
            String line;
            while((line=br.readLine())!=null) {
                if(line.isBlank()||line.startsWith("$"))continue;

                String[]parts=line.split("\\|", -1);
                if(parts.length<6) continue;;

                String tsStr=parts[0].trim();
                String clientId=parts[2].trim();
                String stage=parts[3].trim();
                String latencyStr=parts[5].trim();

                if(!stagesFilter.contains(stage))continue;

                Instant ts = parseTs(tsStr);
                if(firstTs==null) firstTs=ts;

                if(warmupSec>0){
                    Instant cutoff=firstTs.plusSeconds(warmupSec);
                    if(ts.isBefore(cutoff)) {continue;}
                }

                double ms;

                try{
                    ms=Double.parseDouble(latencyStr);
                }catch(NumberFormatException e){
                    continue;
                }
                if(ms<0) continue;

                out.computeIfAbsent(clientId, k->new LinkedHashMap<>())
                        .computeIfAbsent(stage, k->new ArrayList<>())
                        .add(ms);
            }
        }
    }
    private static Instant parseTs(String tsStr) {
        LocalDateTime ldt=LocalDateTime.parse(tsStr, TS_FMT);
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static void writeSummaryCsv(
            Path out,
            String scenarioId,
            Map<String, Map<String, List<Double>>> data,
            List<String> stages
    )throws IOException {
        boolean writeHeader=!Files.exists(out)||Files.size(out)==0;
        try(BufferedWriter bw=Files.newBufferedWriter(out,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if(writeHeader) {
                    bw.write("scenario,clientID,stage,samples,p95_ms,p99_ms,max_ms\n");
                }

            for(String stage: stages) {
                for(Map.Entry<String, Map<String, List<Double>>> e: data.entrySet()) {
                    String clientId=e.getKey();
                    List<Double> list=e.getValue().getOrDefault(stage, List.of());

                    if (list.isEmpty()) {
                        bw.write(String.format(Locale.US,
                                "%s,%s,%s,%d,,,\n",
                                scenarioId, clientId, stage, 0));
                        continue;
                    }

                    List<Double> sorted=new ArrayList<>(list);
                    Collections.sort(sorted);

                    int n= sorted.size();
                    double p95=percentile(sorted, 0.95);
                    double p99=percentile(sorted,0.99);
                    double max=sorted.get(n-1);

                    bw.write(String.format(Locale.US,
                            "%s,%s,%s,%d,%.3f,%.3f,%.3f\n",
                            scenarioId, clientId, stage, n, p95, p99, max));
                }
            }
        }
    }
    private static double percentile(List<Double> sorted, double percentile) {
        int n=sorted.size();
        int idx=(int)Math.ceil(n*percentile)-1;
        if(idx<0) idx=0;
        if(idx>=n) idx=n-1;
        return sorted.get(idx);
    }
}
