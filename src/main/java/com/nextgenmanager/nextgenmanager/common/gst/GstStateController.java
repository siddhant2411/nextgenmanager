package com.nextgenmanager.nextgenmanager.common.gst;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/common")
public class GstStateController {

    @GetMapping("/gst-states")
    public ResponseEntity<List<Map<String, String>>> gstStates() {
        return ResponseEntity.ok(GstState.toList());
    }
}
