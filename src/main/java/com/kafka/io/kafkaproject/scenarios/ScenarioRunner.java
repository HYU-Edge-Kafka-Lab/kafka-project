package com.kafka.io.kafkaproject.scenarios;

import com.kafka.io.kafkaproject.clients.consumer.NormalConsumer;
import com.kafka.io.kafkaproject.clients.producer.HeavyProducer;
import com.kafka.io.kafkaproject.clients.producer.LightProducer;

import java.util.ArrayList;
import java.util.List;

/**
 * 실험 시나리오 실행기
 *
 * 시나리오 구성:
 * - S0  : Heavy/Light 동일 부하 baseline 비교
 * - S1  : Heavy 고부하 / Light 단일 connection 비교
 * - S1N : Heavy 고부하 / 다중 Light connection 비교
 *
 * 현재 실험 파라미터:
 * - S0  : Heavy 1000 TPS, Light 1000 TPS
 * - S1  : Heavy target 10000 TPS, Light 1000 TPS
 * - S1N : Heavy target 10000 TPS, Light 5개 × 200 TPS (총 1000 TPS)
 *
 * 목적:
 * - 동일 부하 환경에서 baseline service gap 확인
 * - 높은 부하를 지속적으로 생성하는 connection과 상대적으로 낮은 부하 connection 간
 *   ACK/service gap 분포 차이를 비교
 * - 다중 Light connection 환경에서 Heavy connection의 처리 빈도 차이를 관찰
 */
public class ScenarioRunner {

    private final String bootstrapServers;

    public ScenarioRunner(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * S0: Heavy/Light 동일 부하 baseline 비교
     * - Heavy 1000 TPS
     * - Light 1000 TPS
     * - baseline service gap 분포 확인 목적
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
     * - Heavy target 10000 TPS
     * - Light 1000 TPS
     * - 높은 부하를 지속적으로 생성하는 connection과 단일 Light connection 간
     *   service gap 분포 차이 관찰 목적
     */
    public void runS1HeavyVsLight(int durationSeconds) {
        final String scenarioId="S1";
        final String groupId="starvation-"+scenarioId;

        try(HeavyProducer heavy=new HeavyProducer(scenarioId, bootstrapServers, 1);
            LightProducer light=new LightProducer(scenarioId, bootstrapServers, 1);
            NormalConsumer consumer=new NormalConsumer(scenarioId, bootstrapServers, groupId, 1)){

            Thread heavyThread=new Thread(()->heavy.runContinuousSend(10_000, durationSeconds), "heavy-S1");
            Thread lightThread=new Thread(()->light.runControlledSend(1000, durationSeconds), "light-S1");
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
     * S1N: Heavy 고부하 / 다중 Light connection 비교
     * - Heavy target 10000 TPS
     * - Light 여러 개가 totalLightTps를 분할하여 전송
     * - 기본 실행 설정: 5개 × 200 TPS = 총 1000 TPS
     * - 다중 Light connection 환경에서 Heavy connection과의 service gap 차이 및
     *   Light connection 간 분포 유사성 관찰 목적
     */
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
        ScenarioRunner runner = new ScenarioRunner("localhost:9092");

        String scenario = args.length > 0 ? args[0] : "S0";
        int duration = args.length > 1 ? Integer.parseInt(args[1]) : 60;

        switch (scenario) {
            case "S0" -> runner.runS0BalancedLoad(duration);
            case "S1" -> runner.runS1HeavyVsLight(duration);
            case "S1N" -> runner.runS1NHeavyVsManyLight(duration, 5, 1000);
            default -> System.out.println("Unknown scenario: " + scenario);
        }
    }
}
