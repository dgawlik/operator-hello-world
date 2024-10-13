package com.github.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("com.github.webserving")
@Version("v1alpha1")
@ShortNames("websrv")
public class WebServingResource extends CustomResource<WebServingSpec, WebServingStatus> implements Namespaced {
}
