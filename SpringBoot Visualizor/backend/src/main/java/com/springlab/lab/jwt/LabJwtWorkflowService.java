package com.springlab.lab.jwt;

import com.springlab.entity.Role;
import com.springlab.lab.jwt.LabJwtDtos.IssuedLabToken;
import com.springlab.repository.UserRepository;
import com.springlab.security.UserAuthInfo;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabJwtWorkflowService {

    private final LabJwtService lab;
    private final LabJwtInspector inspector;
    private final UserRepository userRepository;

    public LabJwtWorkflowService(LabJwtService lab, LabJwtInspector inspector, UserRepository userRepository) {
        this.lab = lab;
        this.inspector = inspector;
        this.userRepository = userRepository;
    }

    /** Issues a lab token for the CALLER only; the database role is the real one, the claimed role is the learner's choice. */
    @Transactional(readOnly = true)
    public IssuedLabToken issueFor(String username, LabTokenVariant variant, Role claimedRole) {
        UserAuthInfo user = userRepository.findAuthInfoByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        String token = lab.issue(variant, user.username(), user.role(), claimedRole);
        return new IssuedLabToken(variant, token, user.role().name(),
                (claimedRole == null ? user.role() : claimedRole).name(),
                "/api/lab/secure/** and /api/lab/jwt/inspect only", inspector.inspect(token));
    }
}
