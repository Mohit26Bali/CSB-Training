package com.icc.assignment.mapper;

import com.icc.assignment.dto.TeamResponse;
import com.icc.assignment.entity.Team;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper {

    public TeamResponse toResponse(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getDescription());
    }
}
