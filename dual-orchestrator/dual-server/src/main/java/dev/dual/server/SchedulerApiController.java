package dev.dual.server;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/scheduler")
public class SchedulerApiController {
    private final SchedulerService schedulerService;

    public SchedulerApiController(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/tick")
    public Map<String, Object> tick(
            @RequestParam(defaultValue = ".") Path workdir,
            @RequestParam(defaultValue = "10") int maxWorkstreams,
            @RequestParam(defaultValue = "1") int maxStepsPerWorkstream
    ) throws Exception {
        return schedulerService.tick(workdir, maxWorkstreams, maxStepsPerWorkstream);
    }
}
