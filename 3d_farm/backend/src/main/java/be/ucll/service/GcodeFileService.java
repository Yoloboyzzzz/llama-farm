package be.ucll.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import be.ucll.model.GcodeFile;
import be.ucll.repository.GcodeFileRepository;

@Service
public class GcodeFileService {

    private final GcodeFileRepository gcodeFileRepository;

    public GcodeFileService(GcodeFileRepository gcodeFileRepository) {
        this.gcodeFileRepository = gcodeFileRepository;
    }

    /**
     * Requeue a G-code file - moves it to end of queue and resets startedAt
     */
    @Transactional
    public void requeueFile(Long id) {
        GcodeFile file = gcodeFileRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("G-code file not found with id: " + id));
        
        // Get current max queue position
        Integer maxPosition = gcodeFileRepository.findMaxQueuePosition();
        int newPosition = (maxPosition != null ? maxPosition : 0) + 1;
        
        // Reset file and move to end of queue
        file.setQueuePostion(newPosition);
        file.setStartedAt(null);
        file.setStatus("waiting");
        file.setLabelPrinted(false);
        
        gcodeFileRepository.save(file);
        
        System.out.println("✅ Requeued: " + file.getName() + " (ID: " + id + ") → Position: " + newPosition);
    }

    /**
     * Delete a G-code file - unlinks it from the job
     * The actual file remains on disk
     */
    @Transactional
    public void deleteFile(Long id) {
        GcodeFile file = gcodeFileRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("G-code file not found with id: " + id));
        
        String filename = file.getName();
        
        // Delete the file record (file on disk remains)
        gcodeFileRepository.delete(file);
        
        System.out.println("✅ Deleted G-code file: " + filename + " (ID: " + id + ")");
        System.out.println("   File remains on disk at: " + file.getPath());
    }
}