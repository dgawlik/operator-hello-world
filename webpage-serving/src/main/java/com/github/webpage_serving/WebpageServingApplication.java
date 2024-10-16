package com.github.webpage_serving;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@RestController
public class WebpageServingApplication {

    @GetMapping(value = "/{page}", produces = "text/html")
    public String page(@PathVariable String page) throws IOException {
        return Files.readString(Path.of("/static/"+page));
    }

    public static void main(String[] args) {
        SpringApplication.run(WebpageServingApplication.class, args);
    }

}
