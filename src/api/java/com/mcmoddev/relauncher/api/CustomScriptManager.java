package com.mcmoddev.relauncher.api;

import java.util.List;

/**
 * The interface used for managing custom user scripts.
 */
public interface CustomScriptManager extends BaseProcessManager {

    /**
     * @return the script
     */
    List<String> getScript();

}
