package com.nextgenmanager.nextgenmanager.production.service;

import com.nextgenmanager.nextgenmanager.production.model.ScheduleDecisionLog;
import com.nextgenmanager.nextgenmanager.production.repository.ScheduleDecisionLogRepository;
import org.springframework.stereotype.Service;

@Service
public class ScheduleDecisionLogServiceImpl implements ScheduleDecisionLogService {

    private final ScheduleDecisionLogRepository scheduleDecisionLogRepository;

    public ScheduleDecisionLogServiceImpl(ScheduleDecisionLogRepository scheduleDecisionLogRepository) {
        this.scheduleDecisionLogRepository = scheduleDecisionLogRepository;
    }

    @Override
    public ScheduleDecisionLog save(ScheduleDecisionLog scheduleDecisionLog) {
        return scheduleDecisionLogRepository.save(scheduleDecisionLog);
    }
}
