package com.github.webpage_serving;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class WebpageServingApplication {

	@RestController
	public class WebpageController {
		@GetMapping(value = "/page1", produces = "text/html")
		public String page1() throws IOException {
			return fileContentOrError("/static/page1");
		}

		@GetMapping(value = "/page2", produces = "text/html")
		public String page2() throws IOException {
			return fileContentOrError("/static/page2");
		}

		private String fileContentOrError(String filename) throws IOException {
			if(!Files.exists(Path.of(filename))) {
				throw new IllegalArgumentException("File not found: " + filename);
			} else {
				return Files.readString(Path.of(filename));
			}
		}
	}

	public static void main(String[] args) {
		System.setProperty("java.net.preferIPv4Stack", "true");
		SpringApplication.run(WebpageServingApplication.class, args);
	}

}
