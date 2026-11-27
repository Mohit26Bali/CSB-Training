package com.icc.assignment.service;

import com.icc.assignment.dto.TeamResponse;
import com.icc.assignment.mapper.TeamMapper;
import com.icc.assignment.repository.TeamRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMapper teamMapper;

    public TeamService(TeamRepository teamRepository, TeamMapper teamMapper) {
        this.teamRepository = teamRepository;
        this.teamMapper = teamMapper;
    }

    public List<TeamResponse> listTeams() {
        return teamRepository.findAll(Sort.by("id")).stream()
                .map(teamMapper::toResponse)
                .toList();
    }
}
