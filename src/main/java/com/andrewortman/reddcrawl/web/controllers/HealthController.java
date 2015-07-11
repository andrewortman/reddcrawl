package com.andrewortman.reddcrawl.web.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.io.IOException;

@RestController
@RequestMapping("/")
public class HealthController {
    private final DataSource dataSource;

    @Autowired
    public HealthController(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Nonnull
    @RequestMapping(method = RequestMethod.GET,
            produces = MediaType.TEXT_PLAIN_VALUE,
            value = "/health/db")
    public ResponseEntity<String> isDBHealthy() throws IOException {
        try {
            dataSource.getConnection();
            return new ResponseEntity<String>("IMOK", HttpStatus.OK);
        } catch (@Nonnull final Exception e) {
            return new ResponseEntity<String>("NOTOK", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
