package com.kafka.io.kafkaproject.analysis.metrics;

import com.kafka.io.kafkaproject.config.ExperimentPaths;

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
 * results/{policy}/{scenarioId}/*.log 파일을 읽어 stage별 latency 통계를 summary.csv로 저장한다.
 * 로그 포맷:
 * ts|thread|clientId|stage|requestId|latency_ms
 *
 * 측정 대상 stage 예시:
 * - ack_received : Producer가 send_start 이후 ACK를 수신하기까지의 외부 응답 latency
 * - service_gap  : 동일 connection에서 연속된 ACK 사이의 시간 간격(보조 지표)
 * - fetch_received / process_done / poll_timeout : Consumer 측 보조 측정 stage
 *
 * 출력 CSV(ROW 방식):
 * scenario,clientID,stage,samples,mean_ms,p0_ms,p10_ms,p20_ms,p30_ms,p40_ms,p50_ms,p60_ms,p70_ms,p80_ms,p90_ms,p95_ms,p99_ms,p100_ms
 *
 * percentile은 mean, p50(중앙값), p90, p95, p99, p100(max)를 포함하며,
 * p0~p100 구간값(10% 단위)도 함께 저장한다.

 * 사용 예시(인텔리J에서 main 실행 + args):
 *   S0 ack_received --warmupSec 5
 *   S1 ack_received service_gap --warmupSec 5
 *   S1N ack_received service_gap --warmupSec 5
 *   S0 fetch_received process_done poll_timeout --warmupSec 5
 */
public class LogSummaryTool {
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        if(args.length < 2) {
            System.out.println("""
                    Usage:
                      <scenarioId> <stage1> [stage2 ...] [--warmupSec N] [--policy default] [--out summary.csv]

                    Example:
                      S0 ack_received --warmupSec 5
                      S1 ack_received service_gap --warmupSec 5 --policy shuffle
                      S1N ack_received service_gap --warmupSec 5 --policy drr
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
        String policy = ExperimentPaths.resolvePolicy();

        while(i<args.length) {
            String opt=args[i];
            if("--warmupSec".equals(opt)) {
                warmupSec=Integer.parseInt(args[i+1]);
                i+=2;
            }else if("--policy".equals(opt)) {
                policy = args[i + 1];
                i += 2;
            }else if("--out".equals(opt)) {
                outName=args[i+1];
                i+=2;
            }else{
                throw new IllegalArgumentException("Unknown option: "+opt);
            }
        }

        Path scenarioDir = ExperimentPaths.scenarioDir(policy, scenarioId);
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
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
                if(writeHeader) {
                    bw.write("scenario,clientID,stage,samples,mean_ms,p0_ms,p10_ms,p20_ms,p30_ms,p40_ms,p50_ms,p60_ms,p70_ms,p80_ms,p90_ms,p95_ms,p99_ms,p100_ms\n");
                }

            for(String stage: stages) {
                for(Map.Entry<String, Map<String, List<Double>>> e: data.entrySet()) {
                    String clientId=e.getKey();
                    List<Double> list=e.getValue().getOrDefault(stage, List.of());

                    if (list.isEmpty()) {
                        bw.write(String.format(Locale.US,
                                "%s,%s,%s,%d,,,,,,,,,,,,,,,,,\n",
                                scenarioId, clientId, stage, 0));
                        continue;
                    }

                    List<Double> sorted = new ArrayList<>(list);
                    Collections.sort(sorted);

                    int n= sorted.size();
                    double sum=0.0;
                    for(double v: sorted) {sum+=v;}
                    double mean=sum/n;

                    double p0=percentile(sorted,0.00);
                    double p10=percentile(sorted,0.10);
                    double p20=percentile(sorted,0.20);
                    double p30=percentile(sorted,0.30);
                    double p40=percentile(sorted,0.40);
                    double p50=percentile(sorted,0.50);
                    double p60=percentile(sorted,0.60);
                    double p70=percentile(sorted,0.70);
                    double p80=percentile(sorted,0.80);
                    double p90=percentile(sorted,0.90);
                    double p95=percentile(sorted, 0.95);
                    double p99=percentile(sorted,0.99);
                    double p100=percentile(sorted,1.00);

                    bw.write(String.format(Locale.US,
                            "%s,%s,%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f\n",
                            scenarioId, clientId, stage, n,
                            mean, p0, p10, p20, p30, p40, p50, p60, p70, p80, p90, p95, p99, p100));
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
