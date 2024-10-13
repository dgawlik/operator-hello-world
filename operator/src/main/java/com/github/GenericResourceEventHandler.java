package com.github;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;

import java.util.function.Consumer;

public class GenericResourceEventHandler<T> implements ResourceEventHandler<T> {

    private final KubernetesClient client;
    private final Consumer<T> handler;

    public GenericResourceEventHandler(KubernetesClient client, Consumer<T> handler) {
        this.client = client;
        this.handler = handler;
    }


    @Override
    public void onAdd(T obj) {
        this.handler.accept(obj);
    }

    @Override
    public void onUpdate(T oldObj, T newObj) {
        this.handler.accept(newObj);
    }

    @Override
    public void onDelete(T obj, boolean deletedFinalStateUnknown) {
        this.handler.accept(null);
    }
}
