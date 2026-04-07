package com.isw2project.consistency;

import com.isw2project.model.Version;

public interface VersionCheck {
    boolean isValid(Version version);
}