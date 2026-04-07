package com.isw2project.consistency.checks;

import com.isw2project.consistency.VersionCheck;
import com.isw2project.model.Version;

public class VersionHasNameCheck implements VersionCheck {
    @Override
    public boolean isValid(Version version) {
        return version.getName() != null && !version.getName().isBlank();
    }
}