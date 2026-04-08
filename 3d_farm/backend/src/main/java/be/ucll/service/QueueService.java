package be.ucll.service;

import be.ucll.dto.QueueItemDTO;
import be.ucll.model.GcodeFile;
import be.ucll.repository.GcodeFileRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QueueService {

    private final GcodeFileRepository repo;

    public QueueService(GcodeFileRepository repo) {
        this.repo = repo;
    }

    /* ----------------------------------------
       Get Queue (waiting only)
    ---------------------------------------- */
    public List<QueueItemDTO> getQueue() {
        return repo.findAllByStatusInOrderByQueuePositionAsc(java.util.List.of("waiting", "sending"))
                .stream()
                .map(g -> {
                    QueueItemDTO dto = new QueueItemDTO();
                    dto.setId(g.getId());
                    dto.setFilename(g.getName());
                    dto.setStatus(g.getStatus());
                    dto.setPrinterModel(g.getModel());
                    dto.setPosition(g.getQueuePostion());
                    dto.setDuration(g.getDuration());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /* ----------------------------------------
       Reorder Queue
    ---------------------------------------- */
    public void reorderQueue(List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            repo.updateQueuePosition(orderedIds.get(i), i + 1);
        }
    }

    /* ----------------------------------------
       Abort Queue Item  ✅ NEW
    ---------------------------------------- */
        public boolean abort(Long gcodeId) {
        Optional<GcodeFile> opt = repo.findById(gcodeId);
        if (opt.isEmpty()) return false;

        GcodeFile gcode = opt.get();

        // Only abort queued or printing jobs
        if (!"waiting".equalsIgnoreCase(gcode.getStatus())
            && !"sending".equalsIgnoreCase(gcode.getStatus())
            && !"printing".equalsIgnoreCase(gcode.getStatus())) {
            return false;
        }

        // ✅ Correct Java values (NO 'none')
        gcode.setStatus("aborted");

        // ✅ Remove from queue
        gcode.setQueuePosition(null);

        // ✅ Detach printer info
        gcode.setPrinter(null);

        repo.save(gcode);
        return true;
    }

}
