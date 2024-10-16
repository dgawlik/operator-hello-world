package com.github;

import com.github.crd.WebServingResource;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class Main {

    private static ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private static Object changes = new Object();
    private static WebServingResource currentCrd;

    public static void main(String[] args) throws InterruptedException {

        KubernetesClient client = new KubernetesClientBuilder().withTaskExecutor(executor).build();

        var crdClient = client.resources(WebServingResource.class).inNamespace("default");


        var handler = new GenericResourceEventHandler<>(update -> {
            synchronized (changes) {
                changes.notifyAll();
            }
        });

        crdClient.inform(handler).start();

        client.apps().deployments().inNamespace("default")
                .withName("web-serving-app-deployment").inform(handler).start();

        client.services().inNamespace("default")
                .withName("web-serving-app-svc").inform(handler).start();

        client.configMaps().inNamespace("default")
                .withName("web-serving-app-config").inform(handler).start();


        for (; ; ) {

            var crdList = crdClient.list().getItems();
            var crd = Optional.ofNullable(crdList.isEmpty() ? null : crdList.get(0));


            var skipUpdate = false;
            var reload = false;

            if (!crd.isPresent()) {
                System.out.println("No WebServingResource found, reconciling disabled");
                currentCrd = null;
                skipUpdate = true;
            } else if (!crd.get().getSpec().equals(
                    Optional.ofNullable(currentCrd)
                            .map(WebServingResource::getSpec).orElse(null))) {
                currentCrd = crd.orElse(null);
                System.out.println("Crd changed, Reconciling ConfigMap");
                reload = true;
            }

            var currentConfigMap = client.configMaps().inNamespace("default")
                    .withName("web-serving-app-config").get();

            if(!skipUpdate && (reload || desiredConfigMap(currentCrd).equals(currentConfigMap))) {
                System.out.println("New configmap, reconciling WebServingResource");
                client.configMaps().inNamespace("default").withName("web-serving-app-config")
                        .createOrReplace(desiredConfigMap(currentCrd));
                reload = true;
            }

            var currentServingDeploymentNullable = client.apps().deployments().inNamespace("default")
                    .withName("web-serving-app-deployment").get();
            var currentServingDeployment = Optional.ofNullable(currentServingDeploymentNullable);

            if(!skipUpdate && (reload || !desiredWebServingDeployment(currentCrd).getSpec().equals(
                    currentServingDeployment.map(Deployment::getSpec).orElse(null)))) {

                System.out.println("Reconciling Deployment");
                client.apps().deployments().inNamespace("default").withName("web-serving-app-deployment")
                        .createOrReplace(desiredWebServingDeployment(currentCrd));
            }

            var currentServingServiceNullable = client.services().inNamespace("default")
                        .withName("web-serving-app-svc").get();
            var currentServingService = Optional.ofNullable(currentServingServiceNullable);

            if(!skipUpdate && (reload || !desiredWebServingService(currentCrd).getSpec().equals(
                    currentServingService.map(Service::getSpec).orElse(null)))) {

                System.out.println("Reconciling Service");
                client.services().inNamespace("default").withName("web-serving-app-svc")
                        .createOrReplace(desiredWebServingService(currentCrd));
            }

            synchronized (changes) {
                changes.wait();
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