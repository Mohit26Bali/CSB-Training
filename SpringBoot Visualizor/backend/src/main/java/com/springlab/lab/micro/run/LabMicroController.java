package com.springlab.lab.micro.run;

import com.springlab.lab.micro.run.MicroDtos.CreateRunRequest;
import com.springlab.lab.micro.run.MicroDtos.Meta;
import com.springlab.lab.micro.run.MicroDtos.RunView;
import com.springlab.lab.micro.run.MicroDtos.TopicInfo;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * The Microservices Lab API. Every route is behind the application authentication (any signed-in role: it is learning material),
 * and every run route is checked against the run's owner (the JWT subject). The only inputs are an experiment id and What-If values
 * that the experiment's own catalogue entry allows.
 */
@RestController
@RequestMapping("/api/lab/microservices")
public class LabMicroController {

    private final MicroRunService service;

    public LabMicroController(MicroRunService service) {
        this.service = service;
    }

    @GetMapping("/meta")
    public Meta meta() {
        return service.meta();
    }

    @GetMapping("/topics")
    public List<TopicInfo> topics() {
        return MicroTopics.ALL;
    }

    @GetMapping("/experiments")
    public List<ExperimentMeta> experiments() {
        return service.catalog();
    }

    @PostMapping("/runs")
    public ResponseEntity<RunView> create(@Valid @RequestBody CreateRunRequest request, @AuthenticationPrincipal Jwt jwt) {
        RunView view = service.create(jwt.getSubject(), request);
        return ResponseEntity.created(URI.create("/api/lab/microservices/runs/" + view.runId())).body(view);
    }

    @GetMapping("/runs/{runId}")
    public RunView get(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        return service.get(jwt.getSubject(), runId);
    }

    @PostMapping("/runs/{runId}/execute")
    public RunView execute(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        return service.execute(jwt.getSubject(), runId);
    }

    @PostMapping("/runs/{runId}/reset")
    public RunView reset(@PathVariable String runId, @AuthenticationPrincipal Jwt jwt) {
        return service.reset(jwt.getSubject(), runId);
    }
}
