package com.nextgenmanager.nextgenmanager.assets.repository;

import com.nextgenmanager.nextgenmanager.assets.model.MachineDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MachineDetailsRepository  extends JpaRepository<MachineDetails,Integer> {
}
