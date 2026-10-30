
package com.klu.crmbackend.dto;

import com.klu.crmbackend.model.User;

public record UserDTO(
        Long userId,
        String name,
        String email,
        String role,
        Long companyId,
        String companyName,
        Long managerId,
        String managerName, boolean active
) {
    public static UserDTO fromUser(User user) {
        return new UserDTO(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),

                user.getCompany() != null
                        ? user.getCompany().getCompanyId()
                        : null,

                user.getCompany() != null
                        ? user.getCompany().getName()
                        : null,

                user.getManager() != null
                        ? user.getManager().getUserId()
                        : null,

                user.getManager() != null
                        ? user.getManager().getName()
                        : null, user.getActive()
        );
    }
}
