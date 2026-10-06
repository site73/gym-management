package com.gym.bdd;

import io.cucumber.java.Before;

/** 每个场景开始前重置 S1 测试世界，保证场景独立、可重复执行。 */
public class Hooks {

    @Before
    public void beforeEachScenario() {
        S1World.reset();
    }
}
