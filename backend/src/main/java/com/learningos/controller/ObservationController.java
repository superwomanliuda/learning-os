package com.learningos.controller;

import com.learningos.dto.ObservationGenerateRequest;
import com.learningos.model.Observation;
import com.learningos.service.ObservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/observation")
public class ObservationController {

    private final ObservationService observationService;

    public ObservationController(ObservationService observationService) {
        this.observationService = observationService;
    }

    @PostMapping("/generate")
    public Observation generate(@Valid @RequestBody ObservationGenerateRequest req) {
        return observationService.generateObservation(req);
    }

    @GetMapping("/student/{studentId}")
    public List<Observation> listByStudent(@PathVariable Long studentId,
                                           @RequestParam(defaultValue = "20") Integer limit) {
        return observationService.listByStudent(studentId, limit);
    }
}
