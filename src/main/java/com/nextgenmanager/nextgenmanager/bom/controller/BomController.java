package com.nextgenmanager.nextgenmanager.bom.controller;

import com.nextgenmanager.nextgenmanager.bom.dto.*;
import com.nextgenmanager.nextgenmanager.bom.mapper.BomMapper;
import com.nextgenmanager.nextgenmanager.bom.model.Bom;
import com.nextgenmanager.nextgenmanager.bom.service.BomExportService;
import com.nextgenmanager.nextgenmanager.bom.service.BomService;
import com.nextgenmanager.nextgenmanager.bom.service.BomWorkflowService;
import com.nextgenmanager.nextgenmanager.bom.service.ResourceNotFoundException;
import com.nextgenmanager.nextgenmanager.common.dto.FilterRequest;
import com.nextgenmanager.nextgenmanager.common.model.FileAttachment;
import com.nextgenmanager.nextgenmanager.production.dto.RoutingDto;
import com.nextgenmanager.nextgenmanager.production.mapper.RoutingMapper;
import com.nextgenmanager.nextgenmanager.production.service.RoutingService;
import io.minio.GetObjectResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

@RestController
@RequestMapping("/api/bom")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_USER','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
@Tag(name = "BOM", description = "Bill of Materials management — components, routing, attachments, cost breakdown")
public class BomController {

    private static final Logger logger = LoggerFactory.getLogger(BomController.class);


    @Autowired
    private BomService bomService;

    @Autowired
    private RoutingService routingService;

    private BOMRoutingMapper bomRoutingMapper;

    @Autowired
    private BomWorkflowService bomWorkflowService;

    @Autowired
    private RoutingMapper routingMapper;

    @Autowired
    private BomExportService bomExportService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getBom(@PathVariable Integer id) {
        logger.info("Received request to fetch BOM with id: {}", id);
        try {
            BomDTO bom = bomService.getBomDTO(id);
            if (bom == null) {
                logger.warn("BOM not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "BOM not found with ID: " + id));
            }
            RoutingDto routingDto =null;
            try {
                routingDto= routingService.getByBom(bom.getId());
            }
            catch (Exception e){
                logger.error(e.getMessage());
            }

            if (routingDto == null) {
                logger.warn("WorkOrderProductionTemplate not found for BOM ID: {}", id);

            }

            BOMRoutingMapper bomTemplateMapper = new BOMRoutingMapper();
            bomTemplateMapper.setBom(bom);
            bomTemplateMapper.setRouting(routingDto);



            return ResponseEntity.ok(bomTemplateMapper);

        } catch (ResourceNotFoundException e) {
            logger.error("Resource not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal error while fetching BOM ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Internal server error occurred"));
        }
    }


    @PostMapping
    public ResponseEntity<?> addBom(@RequestBody BOMRoutingRequestMapper bomRoutingRequestMapper) {
        logger.debug("Received request to add new BOM");

        try {

            Bom bom = bomService.addBom(bomRoutingRequestMapper.toBomEntity());
            RoutingDto routingDto = routingService.createOrUpdateRouting(bom.getId(),routingMapper.toDTO(bomRoutingRequestMapper.getRouting()),"SYSTEM");

            BOMRoutingMapper bomRoutingMapper = new BOMRoutingMapper();
            bomRoutingMapper.setBom(BomMapper.toDto(bom));
            bomRoutingMapper.setRouting(routingDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(bomRoutingMapper);

        } catch (Exception e) {
            logger.error("Failed to create BOM: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create BOM: " + e.getMessage()));
        }
    }


    @PutMapping("/{id}")
    public ResponseEntity<?> updateBom(@PathVariable Integer id, @RequestBody BOMRoutingRequestMapper bomRoutingRequestMapper) {
        try{
            Bom bom = bomService.editBom(id,bomRoutingRequestMapper.toBomEntity());
            RoutingDto routingDto = routingService.createOrUpdateRouting(bom.getId(),routingMapper.toDTO(bomRoutingRequestMapper.getRouting()),"SYSTEM");

            BOMRoutingMapper bomRoutingMapper = new BOMRoutingMapper();
            bomRoutingMapper.setBom(BomMapper.toDto(bom));
            bomRoutingMapper.setRouting(routingDto);
            return ResponseEntity.ok(bomRoutingMapper);

        } catch (Exception e) {
            logger.error("Failed to update BOM with ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update BOM: " + e.getMessage()));
        }
    }

    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN','ROLE_PRODUCTION_USER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBom(@PathVariable Integer id) {
        logger.info("Received request to delete BOM with id: {}", id);
        try {
            Bom deletedBom = bomService.deleteBom(id);

            BOMRoutingMapper responseMapper = new BOMRoutingMapper();
            responseMapper.setBom(BomMapper.toDto(deletedBom));

            logger.info("Successfully deleted BOM ID: {}", id);
            return ResponseEntity.status(HttpStatus.OK).body("Bom with id: "+id+" is deleted");
        } catch (Exception e) {
            logger.error("Failed to delete BOM ID {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to delete BOM: " + e.getMessage()));
        }
    }

    @GetMapping("/active-by-item/{itemId}")
    public ResponseEntity<?> getBomByItem(@PathVariable Integer itemId) {
        logger.debug("Received request to get BOMs by item ID: {}", itemId);
        try {
                BomDTO activeBom = bomService.getActiveBomByParentInventoryItem(itemId);
                RoutingDto routingDto = routingService.getByBom(activeBom.getId());

                BOMRoutingMapper bomRoutingMapper = new BOMRoutingMapper();
                bomRoutingMapper.setBom(activeBom);
                bomRoutingMapper.setRouting(routingDto);
            return ResponseEntity.ok(bomRoutingMapper);


        }
        catch (ResourceNotFoundException e) {
            logger.error("BOM not found for item ID {}: {}", itemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error",e.getMessage()));
        }
        catch (Exception e) {
            logger.error("Failed to fetch BOMs by item ID {}: {}", itemId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch BOMs: " + e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBoms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "") String search){
        logger.debug("Received request to fetch all BOMs with pagination and sorting");
        try {
            Page<Bom> items = bomService.getAllBom(page, size, sortBy, sortDir, search);
            Page<BomDTO> dtoPage = items.map(BomMapper::toDto);
            return ResponseEntity.ok(dtoPage);

        } catch (Exception e) {
            logger.error("Error fetching all BOMs: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/{id}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@PathVariable int id, @RequestParam("file") MultipartFile file) {
        try {

            bomService.saveAttachment(id, file);

            return ResponseEntity.ok("File uploaded successfully!");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading file: " + e.getMessage());
        }
    }


    @PostMapping("/filter")
    public Page<BomListDTO> filterInventoryItems(@RequestBody FilterRequest request) {
        return bomService.filterBom(request);
    }

    @PostMapping("/active/search")
    public Page<BomListDTO> searchActiveBom(@RequestBody SearchRequest request) {
        return bomService.searchActiveBom(request.getQuery());
    }

    @GetMapping("/positions/{bomId}")
    public ResponseEntity<?> getBomPositions(@PathVariable int bomId){

        try {
            List<BomPositionDTO> bomPositionDTO = bomService.getBomPositionsDTO(bomId);
            return ResponseEntity.status(200).body(bomPositionDTO);
        }
        catch (ResourceNotFoundException e){
            logger.error("Bom not found with id - {} : {}",bomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }catch (Exception e){

            logger.error("Error retrieving the bom positions for bomId - {} : {} ",bomId,e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error","Something Went Wrong"));

        }

    }

    @GetMapping("/positions/by-item/{itemId}")
    public ResponseEntity<?> getPositionsByItemActiveBom(@PathVariable int itemId){
        try {
            List<BomPositionDTO> positions = bomService.getPositionsByItemActiveBom(itemId);
            return ResponseEntity.ok(positions);
        }
        catch (ResourceNotFoundException e){
            logger.error("Item not found with id - {} : {}",itemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }catch (Exception e){
            logger.error("Error retrieving positions for item - {} : {} ",itemId,e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error","Something Went Wrong"));
        }
    }

    @PostMapping("/changeStatus/{bomId}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_PRODUCTION_ADMIN')")
    public ResponseEntity<?> changeBomStatus(@PathVariable int bomId,@RequestBody BomStatusChangeRequest bomStatusChangeRequest){
        try {
            BomDTO bomDTO = bomService.changeBomStatus(bomId,bomStatusChangeRequest);
            ResponseEntity.status(200).body(bomDTO);
            return ResponseEntity.status(HttpStatus.OK).body(bomDTO);
        }
        catch (ResourceNotFoundException e){
            logger.error("Bom not found with id - {} : {}",bomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
        catch (IllegalStateException e){
            logger.error("Wrong Status change for BOMid: {} - {}",bomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body(e.getMessage());
        }
        catch (Exception e){

            logger.error("Error retrieving the bom positions for bomId - {} : {} ",bomId,e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something Went Wrong");

        }
    }

    @GetMapping("/{bomId}/attachments")
    public ResponseEntity<?> getBomAttachments(@PathVariable int bomId){

        try {
            List<FileAttachment> fileAttachments = bomService.getAttachments(bomId);

            return ResponseEntity.status(HttpStatus.OK).body(fileAttachments);
        }catch (Exception e){
            logger.error("Something went wrong while fetching attachments for bomId: {} ",bomId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong fetching attachments");
        }

    }


    @PostMapping(value ="/upload/{bomId}",
            consumes = { MediaType.MULTIPART_FORM_DATA_VALUE },
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadBomAttachment(@PathVariable int bomId, @RequestPart MultipartFile file) throws Exception {
        try {
            logger.debug("Received request to upload the file for bomId: {}", bomId);
            bomService.uploadFile(bomId,file);
            return ResponseEntity.status(HttpStatus.OK).body("File Uploaded Successfully");
        }catch (ResourceNotFoundException e){
            logger.error("Bom not found with id - {} : {}",bomId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }catch (Exception e){
            logger.error("Something went wrong while uploading attachments for bomId: {} ",bomId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong fetching attachments");
        }

    }


    @DeleteMapping("{bomId}/delete-attachment/{fileId}")
    public ResponseEntity<?> deleteBomAttachment(@PathVariable int bomId, @PathVariable long fileId){
        try {
            logger.debug("Deleting bom attachment with id: {} for bomId: {}",fileId, bomId);
            bomService.deleteAttachment(bomId,fileId);
            return ResponseEntity.status(HttpStatus.OK).body("File Deleted Successfully");
        }
        catch (ResourceNotFoundException e){
            logger.error("File not found with id: {}",fileId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found with id: "+fileId);
        }
        catch (Exception e) {
            logger.error("Error Deleting file for bom with file id: {}",fileId);
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error deleting the file");
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<?> downloadFile(@PathVariable Long fileId) {
        try {
            GetObjectResponse file = bomService.downloadBomAttachment(fileId);
            FileAttachment fileAttachment = bomService.getAttachment(fileId);
            if (file == null || fileAttachment ==null) {
                logger.error("File not found with ID: " + fileId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found with ID: " + fileId);
            }

            byte[] fileBytes = file.readAllBytes();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileAttachment.getContentType() != null
                            ? fileAttachment.getContentType()
                            : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileAttachment.getOriginalName() + "\"")
                    .body(fileBytes);

        } catch (ResourceNotFoundException ex) {
            logger.error("File not found with id: {}",fileId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        } catch (MalformedURLException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch ( Exception e) {
            logger.error("Error while downloading file with id: {} {}",fileId,e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong");
        }
    }


    @PostMapping("/{bomId}/duplicate")
    public ResponseEntity<?> duplicateBom(@PathVariable int bomId){
        try {
            BOMRoutingMapper bom = bomService.duplicateBom(bomId);
            return ResponseEntity.status(HttpStatus.OK).body(bom);
        }catch (ResourceNotFoundException e){
            return  ResponseEntity.status(HttpStatus.NOT_FOUND).body("Bom not found");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Something went wrong");
        }
    }


    @GetMapping("/export/flat")
    public ResponseEntity<byte[]> exportFlatBom(@RequestParam List<Integer> ids) {
        try {
            byte[] fileBytes = bomExportService.generateFlatBomExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Flat_BOM_Export.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating flat BOM export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/export/indented")
    public ResponseEntity<byte[]> exportIndentedBom(@RequestParam List<Integer> ids) {
        try {
            byte[] fileBytes = bomExportService.generateIndentedBomExcel(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Indented_BOM_Export.xlsx\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating indented BOM export: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportManufacturingBomPdf(@RequestParam List<Integer> ids) {
        try {
            byte[] fileBytes = bomExportService.generateManufacturingBomPdf(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Manufacturing_BOM_Sheet.pdf\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating manufacturing BOM PDF: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/export/job-sheet")
    public ResponseEntity<byte[]> exportBomJobSheet(@RequestParam List<Integer> ids) {
        try {
            byte[] fileBytes = bomExportService.generateBomJobSheet(ids);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"BOM_Job_Sheet.pdf\"")
                    .body(fileBytes);
        } catch (Exception e) {
            logger.error("Error generating BOM job sheet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/{itemId}/bom-history")
    public ResponseEntity<?> getBomHistoryByInventoryItem(@PathVariable int itemId) {
        try {
            List<BomDTO> bomHistory = bomService.getBomHistoryByParentInventoryItem(itemId);
            return ResponseEntity.ok(bomHistory);
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","No BOM history found for the given inventory item"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error","Something went wrong while fetching BOM history"));
        }
    }


    @GetMapping("/{bomId}/change-log")
    public ResponseEntity<List<ChangeLogDto>> getChangeLog(@PathVariable("bomId") int bomId) {
        List<ChangeLogDto> log = bomService.getChangeLogForBom(bomId);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/{bomId}/cost-breakdown")
    public ResponseEntity<?> getBomCostBreakdown(@PathVariable int bomId) {
        try {
            BomCostBreakdownDTO breakdown = bomService.getBomCostBreakdown(bomId);
            return ResponseEntity.ok(breakdown);
        } catch (ResourceNotFoundException e) {
            logger.error("BOM not found for cost breakdown, ID: {}", bomId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error calculating cost breakdown for BOM {}: {}", bomId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to calculate cost breakdown: " + e.getMessage()));
        }
    }

    @GetMapping("/where-used/{itemId}")
    public ResponseEntity<?> getUsedByBoms(
            @PathVariable int itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
        ){

            Page<BomListDTO> usedByBoms = bomService.getBomsUsingInventoryItem(itemId,page,size,sortBy,sortDir);
            return ResponseEntity.ok(usedByBoms);

    }
}



