package com.kafka.io.kafkaproject.scenarios;

import com.kafka.io.kafkaproject.clients.consumer.NormalConsumer;
import com.kafka.io.kafkaproject.clients.producer.HeavyProducer;
import com.kafka.io.kafkaproject.clients.producer.LightProducer;
import com.kafka.io.kafkaproject.config.KafkaConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 실험 시나리오 실행기
 *
 * 시나리오 구성:
 * - S0  : Heavy/Light 동일 부하 baseline 비교
 * - S1  : Heavy 고부하 / Light 단일 connection 비교
 * - S2  : 다중 Heavy connection / Light 단일 connection 비교
 * - S1N : Heavy 단일 connection / 다중 Light connection 비교
 * 목적:
 * - S0  : 동일 producer 설정과 동일 TPS에서 baseline 확보
 * - S1  : 동일 producer 설정에서 TPS 비대칭만 주었을 때 외부 응답 편차 관찰
 * - S2  : aggregate heavy load를 유지한 채 heavy 채널 수를 늘렸을 때 차이 관찰
 * - S1N : 총 유입량은 유지하면서 light connection 수를 늘렸을 때 편차 관찰
 *
 * service_gap은 보조 지표이고, 3장 실험 해석의 중심은 ack_received와
 * connection 구성 변화에 따른 외부 응답 편차다.
 */
public class ScenarioRunner {

    private final String bootstrapServers;

    public ScenarioRunner(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * S0: Heavy/Light 동일 부하 baseline 비교
     * - Heavy/Light 동일 producer 설정
     * - Heavy 500 TPS, Light 500 TPS
     * - baseline 외부 응답 분포 확인
     */
    public void runS0BalancedLoad(int durationSeconds) {
        final String scenarioId="S0";
        final String groupId="starvation-"+scenarioId;

        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers, 1);
            LightProducer light=new LightProducer(scenarioId, bootstrapServers, 1);
            NormalConsumer consumer=new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)){

            Thread heavyThread=new Thread(() -> heavy.runContinuousSend(KafkaConfig.S0_HEAVY_TPS, durationSeconds), "heavy-S0");
            Thread lightThread=new Thread(() -> light.runControlledSend(KafkaConfig.S0_LIGHT_TPS, durationSeconds), "light-S0");
            Thread consumerThread=new Thread(consumer::runConsume, "normal-consumer-S0");

            consumerThread.start();
            heavyThread.start();
            lightThread.start();


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
     * S1: Heavy 고부하 / Light 단일 connection 비교
     * - Heavy/Light 동일 producer 설정
     * - Heavy 3000 TPS, Light 500 TPS
     * - TPS 비대칭이 외부 응답 분포에 주는 영향 관찰
     */
    public void runS1HeavyVsLight(int durationSeconds) {
        final String scenarioId="S1";
        final String groupId="starvation-"+scenarioId;

        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers, 1);
            LightProducer light=new LightProducer(scenarioId, bootstrapServers, 1);
            NormalConsumer consumer=new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)){

            Thread heavyThread=new Thread(() -> heavy.runContinuousSend(KafkaConfig.S1_HEAVY_TPS, durationSeconds), "heavy-S1");
            Thread lightThread=new Thread(() -> light.runControlledSend(KafkaConfig.S1_LIGHT_TPS, durationSeconds), "light-S1");
            Thread consumerThread=new Thread(consumer::runConsume, "normal-consumer-S1");

            consumerThread.start();
            heavyThread.start();
            lightThread.start();

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

    /**
     * S2: 다중 Heavy connection / Light 단일 connection 비교
     * - Heavy 6개 x 500 TPS = 총 3000 TPS
     * - Light 1개 x 500 TPS
     * - aggregate ingress는 S1과 유사하지만, selector 경쟁 채널 수를 늘린다.
     */
    public void runS2ManyHeavyVsSingleLight(int durationSeconds, int heavyCount, int heavyTpsPerConnection,
                                            int lightCount, int lightTpsPerConnection) {
        final String scenarioId = "S2";
        final String groupId = "starvation-" + scenarioId;

        try (NormalConsumer consumer = new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)) {
            List<HeavyProducer> heavies = new ArrayList<>();
            for (int i = 1; i <= heavyCount; i++) {
                heavies.add(new HeavyProducer(scenarioId, bootstrapServers, i));
            }

            List<LightProducer> lights = new ArrayList<>();
            for (int i = 1; i <= lightCount; i++) {
                lights.add(new LightProducer(scenarioId, bootstrapServers, i));
            }

            List<Thread> heavyThreads = new ArrayList<>();
            for (int i = 0; i < heavies.size(); i++) {
                HeavyProducer hp = heavies.get(i);
                Thread t = new Thread(
                        () -> hp.runContinuousSend(heavyTpsPerConnection, durationSeconds),
                        "heavy-" + scenarioId + "-" + (i + 1)
                );
                heavyThreads.add(t);
            }

            List<Thread> lightThreads = new ArrayList<>();
            for (int i = 0; i < lights.size(); i++) {
                LightProducer lp = lights.get(i);
                Thread t = new Thread(
                        () -> lp.runControlledSend(lightTpsPerConnection, durationSeconds),
                        "light-" + scenarioId + "-" + (i + 1)
                );
                lightThreads.add(t);
            }

            Thread consumerThread = new Thread(consumer::runConsume, "normal-consumer-" + scenarioId);

            consumerThread.start();
            heavyThreads.forEach(Thread::start);
            lightThreads.forEach(Thread::start);

            Thread.sleep(durationSeconds * 1000L);
            consumer.stop();

            for (Thread t : heavyThreads) t.join();
            for (Thread t : lightThreads) t.join();
            consumerThread.join();

            for (HeavyProducer hp : heavies) hp.close();
            for (LightProducer lp : lights) lp.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("S2 interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("S2 failed", e);
        }
    }

    /**
     * S1N: Heavy 단일 connection / 다중 Light connection 비교
     * - Heavy 1000 TPS
     * - Light 5개 × 500 TPS
     * - 총 유입량을 유지하면서 connection 수 증가 효과를 관찰
     */
    public void runS1NHeavyVsManyLight(int durationSeconds, int lightCount, int tpsPerLight) {
        final String scenarioId = "S1N";
        final String groupId = "starvation-" + scenarioId;

        try (HeavyProducer heavy = new HeavyProducer(scenarioId, bootstrapServers, 1);
             NormalConsumer consumer = new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)) {

            // Light producers
            List<LightProducer> lights = new ArrayList<>();
            for (int i = 1; i <= lightCount; i++) {
                lights.add(new LightProducer(scenarioId, bootstrapServers, i));
            }

            Thread heavyThread = new Thread(() -> heavy.runContinuousSend(KafkaConfig.S1N_HEAVY_TPS, durationSeconds), "heavy-" + scenarioId);
            Thread consumerThread = new Thread(consumer::runConsume, "normal-consumer-" + scenarioId);

            List<Thread> lightThreads = new ArrayList<>();
            for (int i = 0; i < lights.size(); i++) {
                LightProducer lp = lights.get(i);
                Thread t = new Thread(() -> lp.runControlledSend(tpsPerLight, durationSeconds), "light-" + scenarioId + "-" + (i + 1));
                lightThreads.add(t);
            }

            consumerThread.start();
            heavyThread.start();
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

    public static void main(String[] args) {
        String bootstrapServers = System.getProperty(
                "experiment.bootstrap",
                System.getenv().getOrDefault("EXPERIMENT_BOOTSTRAP", "localhost:9092")
        );
        ScenarioRunner runner = new ScenarioRunner(bootstrapServers);

        String scenario = args.length > 0 ? args[0] : "S0";
        int duration = args.length > 1 ? Integer.parseInt(args[1]) : 60;

        switch (scenario) {
            case "S0" -> runner.runS0BalancedLoad(duration);
            case "S1" -> runner.runS1HeavyVsLight(duration);
            case "S2" -> runner.runS2ManyHeavyVsSingleLight(
                    duration,
                    KafkaConfig.S2_HEAVY_COUNT,
                    KafkaConfig.S2_HEAVY_TPS_PER_CONNECTION,
                    KafkaConfig.S2_LIGHT_COUNT,
                    KafkaConfig.S2_LIGHT_TPS_PER_CONNECTION
            );
            case "S1N" -> runner.runS1NHeavyVsManyLight(
                    duration,
                    KafkaConfig.S1N_LIGHT_COUNT,
                    KafkaConfig.S1N_LIGHT_TPS_PER_CONNECTION
            );
            default -> System.out.println("Unknown scenario: " + scenario);
        }
    }
}
