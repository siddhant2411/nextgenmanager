package com.nextgenmanager.nextgenmanager.marketing.template.controller;

import com.nextgenmanager.nextgenmanager.marketing.template.model.MessageTemplate;
import com.nextgenmanager.nextgenmanager.marketing.template.model.TemplateChannel;
import com.nextgenmanager.nextgenmanager.marketing.template.repository.MessageTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.nextgenmanager.nextgenmanager.common.security.authorization.RequiresSalesAccess;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/message-template")
@RequiresSalesAccess
public class MessageTemplateController {

    private static final Logger logger = LoggerFactory.getLogger(MessageTemplateController.class);
    private final MessageTemplateRepository repo;

    public MessageTemplateController(MessageTemplateRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<MessageTemplate>> getAll() {
        return ResponseEntity.ok(repo.findByDeletedDateIsNullOrderByNameAsc());
    }

    @GetMapping("/channel/{channel}")
    public ResponseEntity<List<MessageTemplate>> getByChannel(@PathVariable TemplateChannel channel) {
        return ResponseEntity.ok(repo.findByChannelAndDeletedDateIsNullOrderByNameAsc(channel));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageTemplate> getById(@PathVariable Long id) {
        return repo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<MessageTemplate> create(@RequestBody MessageTemplate template) {
        try {
            MessageTemplate saved = repo.save(template);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            logger.error("Error creating template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<MessageTemplate> update(@PathVariable Long id, @RequestBody MessageTemplate template) {
        return repo.findById(id).map(existing -> {
            existing.setName(template.getName());
            existing.setBody(template.getBody());
            existing.setSubject(template.getSubject());
            existing.setChannel(template.getChannel());
            existing.setCategory(template.getCategory());
            existing.setDefault(template.isDefault());
            return ResponseEntity.ok(repo.save(existing));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return repo.findById(id).map(existing -> {
            existing.setDeletedDate(new Date());
            repo.save(existing);
            return ResponseEntity.ok(Map.of("message", "Template deleted"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
