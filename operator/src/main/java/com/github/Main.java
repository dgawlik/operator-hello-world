package com.github;

import com.github.crd.WebServingResource;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Main {

    private static ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static Object changes = new Object();
    private static WebServingResource currentCrd;
    private static WebServingResource previousCrd;
    private static Deployment currentServingDeployment;
    private static Service currentServingService;
    private static ConfigMap currentConfigMap;

    public static void main(String[] args) throws InterruptedException {

        KubernetesClient client = new KubernetesClientBuilder().withTaskExecutor(executor).build();

        var crdClient = client.resources(WebServingResource.class).inNamespace("default");

        var crd = crdClient.list().getItems();

        if (crd.isEmpty()) {
            currentCrd = null;
        } else {
            currentCrd = crd.get(0);
        }

        currentServingDeployment = client.apps().deployments().inNamespace("default")
                .withName("web-serving-app-deployment").get();

        currentServingService = client.services().inNamespace("default")
                .withName("web-serving-app-svc").get();

        currentConfigMap = client.configMaps().inNamespace("default")
                .withName("web-serving-app-config").get();


        crdClient.inform(new GenericResourceEventHandler<>(client, update -> {
            synchronized (changes) {
                previousCrd = currentCrd;
                currentCrd = update;
                changes.notifyAll();
            }
        })).start();

        client.apps().deployments().inNamespace("default").withName("web-serving-app-deployment").inform(new GenericResourceEventHandler<>(client, update -> {
            synchronized (changes) {
                currentServingDeployment = update;
                changes.notifyAll();
            }
        })).start();

        client.services().inNamespace("default").withName("web-serving-app-svc").inform(new GenericResourceEventHandler<>(client, update -> {
            synchronized (changes) {
                currentServingService = update;
                changes.notifyAll();
            }
        })).start();

        client.configMaps().inNamespace("default").withName("web-serving-app-config").inform(new GenericResourceEventHandler<>(client, update -> {
            synchronized (changes) {
                currentConfigMap = update;
                changes.notifyAll();
            }
        })).start();


        for (; ; ) {
            if (currentCrd == null) {
                System.out.println("No WebServingResource found, reconciling disabled");
            } else {
                if (!currentCrd.equals(previousCrd)
                        || currentConfigMap == null ||
                        !desiredConfigMap(currentCrd).equals(currentConfigMap)) {
                    System.out.println("Reconciling ConfigMap");
                    client.configMaps().withName("web-serving-app-config")
                            .createOrReplace(desiredConfigMap(currentCrd));
                }

                if (currentConfigMap == null) {
                    System.out.println("No ConfigMap found, no point in reconciling web app for now");
                } else {
                    System.out.println("Reconciling WebServingResource");
                    reconcileServingApp(client, currentCrd, !currentCrd.equals(previousCrd));
                }

            }

            synchronized (changes) {
                changes.wait();
            }
        }

    }

    private static void reconcileServingApp(KubernetesClient client, WebServingResource crd, boolean forceUpdate) {

        if (forceUpdate){
            client.apps().deployments().inNamespace("default").withName("web-serving-app-deployment")
                    .createOrReplace(desiredWebServingDeployment(crd));
            client.services().inNamespace("default").withName("web-serving-app-svc")
                    .createOrReplace(desiredWebServingService(crd));
            return;
        }

        if (currentServingDeployment == null) {
            client.apps().deployments().inNamespace("default").withName("web-serving-app-deployment")
                    .create(desiredWebServingDeployment(crd));
        } else {

            if (desiredWebServingDeployment(crd).getSpec().equals(currentServingDeployment.getSpec())
            ) {
                client.apps().deployments().inNamespace("default").withName("web-serving-app-pod").edit(d -> desiredWebServingDeployment(crd));
            }
        }

        if (currentServingService == null) {
            client.services().inNamespace("default").withName("web-serving-app-svc")
                    .create(desiredWebServingService(crd));
        } else {

            if (desiredWebServingService(crd).getSpec().equals(currentServingService.getSpec())
            ) {
                client.services().inNamespace("default").withName("web-serving-app-svc").edit(p -> desiredWebServingService(crd));
            }
        }
    }


    private static Deployment desiredWebServingDeployment(WebServingResource crd) {
        return new DeploymentBuilder()
                .withNewMetadata()
                .withName("web-serving-app-deployment")
                .withNamespace("default")
                .addToLabels("app", "web-serving-app")
                .withOwnerReferences(createOwnerReference(crd))
                .endMetadata()
                .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                .addToMatchLabels("app", "web-serving-app")
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("app", "web-serving-app")
                .endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName("web-serving-app-container")
                .withImage("ghcr.io/dgawlik/webpage-serving:1.0.5")
                .withVolumeMounts(new VolumeMountBuilder()
                        .withName("web-serving-app-config")
                        .withMountPath("/static")
                        .build())
                .addNewPort()
                .withContainerPort(8080)
                .endPort()
                .endContainer()
                .withVolumes(new VolumeBuilder()
                        .withName("web-serving-app-config")
                        .withConfigMap(new ConfigMapVolumeSourceBuilder()
                                .withName("web-serving-app-config")
                                .build())
                        .build())
                .withImagePullSecrets(new LocalObjectReferenceBuilder()
                        .withName("regcred").build())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private static Service desiredWebServingService(WebServingResource crd) {
        return new ServiceBuilder()
                .editMetadata()
                .withName("web-serving-app-svc")
                .withOwnerReferences(createOwnerReference(crd))
                .withNamespace(crd.getMetadata().getNamespace())
                .endMetadata()
                .editSpec()
                .addNewPort()
                .withPort(8080)
                .withTargetPort(new IntOrString(8080))
                .endPort()
                .addToSelector("app", "web-serving-app")
                .endSpec()
                .build();
    }

    private static ConfigMap desiredConfigMap(WebServingResource crd) {
        return new ConfigMapBuilder()
                .withMetadata(
                        new ObjectMetaBuilder()
                                .withName("web-serving-app-config")
                                .withNamespace(crd.getMetadata().getNamespace())
                                .withOwnerReferences(createOwnerReference(crd))
                                .build())
                .withData(Map.of("page1", crd.getSpec().page1(),
                        "page2", crd.getSpec().page2()))
                .build();
    }

    private static OwnerReference createOwnerReference(WebServingResource crd) {
        return new OwnerReferenceBuilder()
                .withApiVersion(crd.getApiVersion())
                .withKind(crd.getKind())
                .withName(crd.getMetadata().getName())
                .withUid(crd.getMetadata().getUid())
                .withController(true)
                .build();
    }
}