package com.kafka.io.kafkaproject.scenarios;

import com.kafka.io.kafkaproject.clients.consumer.NormalConsumer;
import com.kafka.io.kafkaproject.clients.producer.HeavyProducer;
import com.kafka.io.kafkaproject.clients.producer.LightProducer;

import java.util.ArrayList;
import java.util.List;

/**
 * 실험 시나리오 실행기
 *
 * 시나리오 정의 (KICKOFF.md 7.1절):
 *
 * | ID | 이름                    | 설명                                    | 기대 결과              |
 * |----|------------------------|----------------------------------------|----------------------|
 * | S0 | Balanced Load          | 동일 TPS로 균형 잡힌 요청                   | 정상 baseline 측정     |
 * | S1 | Heavy vs Light Producer| Heavy 연속 전송, Light 간헐적 전송          | Read 편향 발생 여부 확인 |
 *
 * 부하 파라미터 (KICKOFF.md 7.2절):
 *
 * | 시나리오 | Heavy TPS | Light TPS | 비율      |
 * |---------|-----------|-----------|----------|
 * | S0      | 1,000     | 1,000     | 1:1      |
 * | S1      | 10,000    | 100       | 100:1    |
 */
public class ScenarioRunner {

    private final String bootstrapServers;

    public ScenarioRunner(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * S0: Balanced Load (대조군)
     * - 정상 baseline 측정
     * - Heavy/Light 각 1,000 TPS
     */
    public void runS0BalancedLoad(int durationSeconds) {
        final String scenarioId="S0";
        final String groupId="starvation-"+scenarioId;

        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers, 1);
            LightProducer light=new LightProducer(scenarioId, bootstrapServers, 1);
            NormalConsumer consumer=new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)){

            Thread heavyThread=new Thread(()->heavy.runContinuousSend(1_000, durationSeconds), "heavy-S0");
            Thread lightThread=new Thread(()->light.runControlledSend(1_000, durationSeconds), "light-S0");
            Thread consumerThread=new Thread(consumer::runConsume, "normal-consumer-S0");

            heavyThread.start();
            lightThread.start();
            consumerThread.start();

            Thread.sleep(durationSeconds*1000L);
            consumer.stop();

            heavyThread.join();
            lightThread.join();
            consumerThread.join();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException("S0 interrupted", e);
        }catch (Exception e){
            throw new RuntimeException("S0 failed", e);
        }
    }


    /**
     * S1: Heavy vs Light Producer
     * - Read 편향 발생 여부 확인
     * - Heavy 10,000 TPS, Light 100 TPS (100:1)
     */
    public void runS1HeavyVsLight(int durationSeconds) {
        final String scenarioId="S1";
        final String groupId="starvation-"+scenarioId;

        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers, 1);
            LightProducer light=new LightProducer(scenarioId, bootstrapServers, 1);
            NormalConsumer consumer=new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)){

            Thread heavyThread=new Thread(()->heavy.runContinuousSend(10_000, durationSeconds), "heavy-S1");
            Thread lightThread=new Thread(()->light.runControlledSend(100, durationSeconds), "light-S1");
            Thread consumerThread=new Thread(consumer::runConsume, "normal-consumer-S1");

            heavyThread.start();
            lightThread.start();
            consumerThread.start();

            Thread.sleep(durationSeconds*1000L);
            consumer.stop();

            heavyThread.join();
            lightThread.join();
            consumerThread.join();
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new RuntimeException("S1 interrupted", e);
        }catch (Exception e){
            throw new RuntimeException("S1 failed", e);
        }

    }
    public void runS1NHeavyVsManyLight(int durationSeconds, int lightCount, int totalLightTps) {
        final String scenarioId = "S1N";
        final String groupId = "starvation-" + scenarioId;

        int tpsPerLight = Math.max(1, totalLightTps / lightCount);

        try (HeavyProducer heavy = new HeavyProducer(scenarioId, bootstrapServers, 1);
             NormalConsumer consumer = new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)) {

            // Light producers
            List<LightProducer> lights = new ArrayList<>();
            for (int i = 1; i <= lightCount; i++) {
                lights.add(new LightProducer(scenarioId, bootstrapServers, i));
            }

            Thread heavyThread = new Thread(() -> heavy.runContinuousSend(10_000, durationSeconds), "heavy-" + scenarioId);
            Thread consumerThread = new Thread(consumer::runConsume, "normal-consumer-" + scenarioId);

            List<Thread> lightThreads = new ArrayList<>();
            for (int i = 0; i < lights.size(); i++) {
                LightProducer lp = lights.get(i);
                Thread t = new Thread(() -> lp.runControlledSend(tpsPerLight, durationSeconds), "light-" + scenarioId + "-" + (i + 1));
                lightThreads.add(t);
            }

            heavyThread.start();
            consumerThread.start();
            lightThreads.forEach(Thread::start);

            Thread.sleep(durationSeconds * 1000L);
            consumer.stop();

            heavyThread.join();
            consumerThread.join();
            for (Thread t : lightThreads) t.join();

            // close lights
            for (LightProducer lp : lights) lp.close();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("S1N interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("S1N failed", e);
        }
    }

//    /**
//     * S2: Slow Consumer Backpressure
//     * - Write 편향 발생 여부 확인
//     * - Heavy 5,000 TPS + SlowConsumer
//     */
//    public void runS2SlowConsumer(int durationSeconds) {
//        // TODO: 구현
//        // 1. HeavyProducer 1개 (5,000 TPS)
//        // 2. SlowConsumer 1개 (500ms 지연)
//        // 3. Send buffer 누적 모니터링
//
//        final String scenarioId="S2";
//        final String groupId="starvation-"+scenarioId;
//
//        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers, 1);
//            SlowConsumer slow=new SlowConsumer(scenarioId, bootstrapServers, groupId, 1)){
//
//            Thread heavyThread=new Thread(()->heavy.runContinuousSend(5_000, durationSeconds), "heavy-S2");
//            Thread slowThread=new Thread(()->{
//                try{
//                    slow.runSlowConsume();
//                }catch (InterruptedException e){
//                    Thread.currentThread().interrupt();
//                }
//            }, "slow-consumer-S2");
//
//            heavyThread.start();
//            slowThread.start();
//
//            Thread.sleep(durationSeconds*1000L);
//            slow.stop();
//
//            heavyThread.join();
//            slowThread.join();
//        }catch (InterruptedException e){
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("S2 interrupted", e);
//        }catch (Exception e){
//            throw new RuntimeException("S2 failed", e);
//        }
//    }
//
//    /**
//     * S3: Control-plane 혼합
//     * - Control 요청 지연 여부 확인
//     * - Heavy 5,000 TPS + Metadata 요청 10 TPS
//     */
//    public void runS3ControlPlane(int durationSeconds) {
//        // TODO: 구현
//        // 1. HeavyProducer 1개 (5,000 TPS)
//        // 2. Metadata 요청 클라이언트 (10 TPS)
//        // 3. Control 요청 latency 측정
//        final String scenarioId="S3";
//
//        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers,1);
//            MetadataRequester meta=new MetadataRequester(scenarioId, bootstrapServers, 1)){
//
//            Thread heavyThread=new Thread(()->heavy.runContinuousSend(5_000,durationSeconds), "heavy-S3");
//            Thread metaThread=new Thread(()->meta.runMetadataRequests(10, durationSeconds), "meta-S3");
//            heavyThread.start();
//            metaThread.start();
//
//            Thread.sleep(durationSeconds*1000L);
//            meta.stop();
//
//            heavyThread.join();
//            metaThread.join();
//        }catch (InterruptedException e){
//            Thread.currentThread().interrupt();
//            throw new RuntimeException("S3 interrupted", e);
//        }
//        catch (Exception e){
//            throw new RuntimeException();
//        }
//    }

    public static void main(String[] args) {
        ScenarioRunner runner = new ScenarioRunner("localhost:9092");

        String scenario = args.length > 0 ? args[0] : "S0";
        int duration = args.length > 1 ? Integer.parseInt(args[1]) : 60;

        switch (scenario) {
            case "S0" -> runner.runS0BalancedLoad(duration);
            case "S1" -> runner.runS1HeavyVsLight(duration);
            case "S1N" -> runner.runS1NHeavyVsManyLight(duration, 5, 100);
            default -> System.out.println("Unknown scenario: " + scenario);
        }
    }
}
