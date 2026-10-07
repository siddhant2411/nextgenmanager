package com.nextgenmanager.nextgenmanager.company.service;

import com.nextgenmanager.nextgenmanager.bom.service.BusinessException;
import com.nextgenmanager.nextgenmanager.company.BrandingFixtures;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrand;
import com.nextgenmanager.nextgenmanager.company.dto.DocumentBrandingDTO;
import com.nextgenmanager.nextgenmanager.company.model.BrandImageKind;
import com.nextgenmanager.nextgenmanager.company.model.BrandingMode;
import com.nextgenmanager.nextgenmanager.company.model.DocumentBranding;
import com.nextgenmanager.nextgenmanager.company.repository.DocumentBrandingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.encode;
import static com.nextgenmanager.nextgenmanager.company.BrandingFixtures.image;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The settings side of document branding: what may be uploaded, what is stored, and that a mode can
 * never be left pointing at an image that is not there.
 */
class DocumentBrandingServiceImplTest {

    private final List<DocumentBranding> stored = new ArrayList<>();
    private DocumentBrandingServiceImpl service;

    @BeforeEach
    void setUp() {
        DocumentBrandingRepository repository = mock(DocumentBrandingRepository.class);
        when(repository.findAll()).thenAnswer(call -> new ArrayList<>(stored));
        when(repository.save(any(DocumentBranding.class))).thenAnswer(call -> {
            stored.clear();
            stored.add(call.getArgument(0));
            return call.getArgument(0);
        });
        service = new DocumentBrandingServiceImpl(repository, BrandingFixtures.companyRepository(), BrandingFixtures.engine());
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "brand.png", "image/png", bytes);
    }

    // ── uploads ───────────────────────────────────────────────────────────────

    @Test
    void anUploadIsScaledDownToPrintSizeAndReEncoded() throws Exception {
        byte[] original = image(4000, 1400);
        DocumentBrandingDTO dto = service.uploadImage(BrandImageKind.LOGO, upload(original));

        assertThat(dto.hasLogo()).isTrue();
        assertThat(dto.logoWidthPx()).isEqualTo(675);
        assertThat(dto.logoHeightPx()).isEqualTo(236);

        DocumentBranding saved = stored.get(0);
        assertThat(saved.getLogoImage()).isNotEqualTo(original);
        assertThat(ImageIO.read(new ByteArrayInputStream(saved.getLogoImage())).getWidth()).isEqualTo(675);
        assertThat(saved.getLogoContentType()).isEqualTo("image/png");
    }

    @Test
    void aSmallLogoIsNotBlownUp() {
        DocumentBrandingDTO dto = service.uploadImage(BrandImageKind.LOGO, upload(image(300, 100)));
        assertThat(dto.logoWidthPx()).isEqualTo(300);
        assertThat(dto.logoHeightPx()).isEqualTo(100);
    }

    @Test
    void aJpegIsAccepted() {
        assertThat(service.uploadImage(BrandImageKind.LETTERHEAD, upload(encode(2000, 350, "jpg"))).hasLetterhead()).isTrue();
    }

    @Test
    void whatIsNotAPngOrJpegIsRefusedWhateverItIsCalled() {
        assertThatThrownBy(() -> service.uploadImage(BrandImageKind.LOGO, upload("<svg onload='x()'/>".getBytes())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("not a PNG or JPEG");
        assertThatThrownBy(() -> service.uploadImage(BrandImageKind.LOGO, upload(encode(400, 400, "gif"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Only PNG and JPEG");
        assertThat(stored).isEmpty();
    }

    @Test
    void anOversizedFileIsRefused() {
        assertThatThrownBy(() -> service.uploadImage(BrandImageKind.LOGO, upload(new byte[3 * 1024 * 1024])))
                .isInstanceOf(BusinessException.class).hasMessageContaining("2 MB");
    }

    @Test
    void aLetterheadMustBeAWideStripAndSharpEnoughToPrint() {
        assertThatThrownBy(() -> service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(1000, 1000))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("wide strip");
        assertThatThrownBy(() -> service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(600, 100))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("blurred");
    }

    // ── mode ──────────────────────────────────────────────────────────────────

    @Test
    void aModeCannotBeChosenBeforeItsImageIsUploaded() {
        assertThatThrownBy(() -> service.updateMode(BrandingMode.LOGO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Upload a logo");
        assertThatThrownBy(() -> service.updateMode(BrandingMode.LETTERHEAD))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Upload a letterhead");

        // Pre-printed paper and plain text need no image.
        assertThat(service.updateMode(BrandingMode.PREPRINTED).mode()).isEqualTo(BrandingMode.PREPRINTED);
        assertThat(service.updateMode(BrandingMode.NONE).mode()).isEqualTo(BrandingMode.NONE);
    }

    @Test
    void removingTheImageInUseFallsBackToText() {
        service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(2000, 353)));
        service.uploadImage(BrandImageKind.LOGO, upload(image(500, 500)));
        service.updateMode(BrandingMode.LETTERHEAD);

        // Removing the image that is not in use leaves the mode alone.
        assertThat(service.removeImage(BrandImageKind.LOGO).mode()).isEqualTo(BrandingMode.LETTERHEAD);

        DocumentBrandingDTO dto = service.removeImage(BrandImageKind.LETTERHEAD);
        assertThat(dto.mode()).isEqualTo(BrandingMode.NONE);
        assertThat(dto.hasLetterhead()).isFalse();
    }

    @Test
    void switchingModeKeepsBothImages() {
        service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(2000, 353)));
        service.uploadImage(BrandImageKind.LOGO, upload(image(500, 500)));
        service.updateMode(BrandingMode.LOGO);

        DocumentBrandingDTO dto = service.updateMode(BrandingMode.LETTERHEAD);
        assertThat(dto.hasLogo()).isTrue();
        assertThat(dto.hasLetterhead()).isTrue();
    }

    // ── what the templates are handed ─────────────────────────────────────────

    @Test
    void theImageIsFittedInsideItsBoxWhateverItsShape() {
        service.uploadImage(BrandImageKind.LOGO, upload(image(500, 500)));
        service.updateMode(BrandingMode.LOGO);
        // A square logo is limited by the 18mm height of the box, not its 45mm width.
        assertThat(service.current().getLogoStyle()).isEqualTo("width:18.0mm;height:18.0mm;");

        service.uploadImage(BrandImageKind.LOGO, upload(image(1600, 200)));
        // A long thin one is limited by the width.
        assertThat(service.current().getLogoStyle()).isEqualTo("width:45.0mm;height:5.6mm;");
    }

    @Test
    void theLetterheadRunsTheFullWidthAndThePrePrintedBandIsThirtyMillimetres() {
        service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(2400, 200)));
        service.updateMode(BrandingMode.LETTERHEAD);
        DocumentBrand thin = service.current();
        assertThat(thin.isReplacesHeader()).isTrue();
        assertThat(thin.getBannerStyle()).contains("width:174.0mm;height:14.5mm;");

        // A deep letterhead still runs the full width until it reaches the 40mm ceiling.
        service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(1740, 400)));
        assertThat(service.current().getBannerStyle()).contains("width:174.0mm;height:40.0mm;");
        service.uploadImage(BrandImageKind.LETTERHEAD, upload(image(1800, 600)));
        assertThat(service.current().getBannerStyle()).contains("width:120.0mm;height:40.0mm;");

        service.updateMode(BrandingMode.PREPRINTED);
        DocumentBrand blank = service.current();
        assertThat(blank.isReplacesHeader()).isTrue();
        assertThat(blank.isBanner()).isFalse();
        assertThat(blank.getBandHeightMm()).isEqualTo(30.0);
    }

    @Test
    void withNothingSavedDocumentsPrintAsTheyAlwaysHave() {
        DocumentBrand brand = service.current();
        assertThat(brand.getMode()).isEqualTo(BrandingMode.NONE);
        assertThat(brand.isReplacesHeader()).isFalse();
        assertThat(brand.isLogo()).isFalse();
        assertThat(brand.isSmallLogo()).isFalse();
        assertThat(brand.getCompanyName()).isEqualTo("Vajra Auto Components Private Limited");
    }
}
