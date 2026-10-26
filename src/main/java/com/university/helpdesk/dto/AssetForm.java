package com.university.helpdesk.dto;

import com.university.helpdesk.entity.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Getter @Setter
public class AssetForm {
    private Long version;
    @NotBlank(message = "Enter an asset tag.") @Size(max = 50, message = "Asset tag must be 50 characters or fewer.")
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*", message = "Use letters, numbers, dots, hyphens or underscores for the asset tag.")
    private String assetTag;
    @NotBlank(message = "Enter an asset name.") @Size(max = 150, message = "Asset name must be 150 characters or fewer.")
    private String assetName;
    @NotNull(message = "Choose an equipment type.")
    private AssetType assetType;
    @Size(max = 100, message = "Serial number must be 100 characters or fewer.")
    private String serialNumber;
    @NotNull(message = "Choose the department responsible for this asset.")
    private Long departmentId;
    @NotBlank(message = "Enter the equipment location.") @Size(max = 150, message = "Location must be 150 characters or fewer.")
    private String location;
    @NotNull(message = "Choose an asset condition.")
    private AssetCondition condition = AssetCondition.WORKING;
    private Long assignedUserId;
    @PastOrPresent(message = "Purchase date cannot be in the future.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;
    @Size(max = 2000, message = "Equipment notes must be 2,000 characters or fewer.")
    private String notes;

    public static AssetForm from(ItAsset asset) {
        var form = new AssetForm();
        form.setVersion(asset.getVersion()); form.setAssetTag(asset.getAssetTag());
        form.setAssetName(asset.getAssetName()); form.setAssetType(asset.getAssetType());
        form.setSerialNumber(asset.getSerialNumber()); form.setDepartmentId(asset.getDepartment().getDepartmentId());
        form.setLocation(asset.getLocation()); form.setCondition(asset.getCondition());
        form.setAssignedUserId(asset.getAssignedUser() == null ? null : asset.getAssignedUser().getUserId());
        form.setPurchaseDate(asset.getPurchaseDate()); form.setNotes(asset.getNotes());
        return form;
    }
}
