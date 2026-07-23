package com.jobdiscovery.source.arbeitnow.dto;

import java.util.List;

/** Top-level Arbeitnow board response; jobs live under "data". */
public record ArbeitnowResponse(List<ArbeitnowJob> data) {
}
