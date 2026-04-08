package be.ucll.controller;

import be.ucll.dto.QueueItemDTO;
import be.ucll.service.QueueService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @GetMapping
    public List<QueueItemDTO> getQueue() {
        return queueService.getQueue();
    }

    @PutMapping("/reorder")
    public void reorderQueue(@RequestBody List<Long> orderedIds) {
        queueService.reorderQueue(orderedIds);
    }

    @PostMapping("/abort/{id}")
    public ResponseEntity<?> abort(@PathVariable Long id) {
        boolean ok = queueService.abort(id);
        return ok
            ? ResponseEntity.ok("Aborted")
            : ResponseEntity.badRequest().body("Cannot abort job");
    }

}
