package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.company.dto.BrandImageDTO;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrand;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrandingDTO;
import com.nextgenmanager.nextgenmanager.company.model.BrandImageKind;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentBrandingService {

    /** The branding every print template renders with. Set it on the template context as {@code brand}. */
    DocumentBrand current();

    DocumentBrandingDTO get();

    DocumentBrandingDTO updateMode(BrandingMode mode);

    DocumentBrandingDTO uploadImage(BrandImageKind kind, MultipartFile file);

    DocumentBrandingDTO removeImage(BrandImageKind kind);

    BrandImageDTO getImage(BrandImageKind kind);

    /** A one-page sample showing the header as it prints, in the saved mode or the one given. */
    byte[] previewPdf(BrandingMode mode);
}
