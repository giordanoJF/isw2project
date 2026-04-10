package com.isw2project.consistency.checks;

import com.isw2project.consistency.VersionCheck;
import com.isw2project.model.Version;

public class VersionHasReleaseDateCheck implements VersionCheck {

    @Override
    public boolean isValid(Version version) {
        return version.getReleaseDate() != null && !version.getReleaseDate().isBlank();
    }
}