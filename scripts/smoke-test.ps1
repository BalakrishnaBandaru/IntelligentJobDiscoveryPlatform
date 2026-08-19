#Requires -Version 5.1
<#
.SYNOPSIS
    End-to-end smoke test for the Intelligent Job Discovery Platform (Phases 0-5).

.DESCRIPTION
    Validates the running Docker stack: app health + DB connectivity, the Flyway
    schema version, the multi-source fetch and its content-hash de-duplication,
    the full candidate-profile lifecycle (upsert, list cleaning, validation,
    cascade delete), and the ranked shortlist the rule engine produces.

    The profile tests are destructive by nature - they delete the profile to
    exercise the 404 path. The script backs up the existing profile first and
    restores it in a finally block, so a crash mid-run still puts it back.

.PARAMETER SkipFetch
    Skip the fetch tests. They call the live Adzuna/Jooble APIs and consume your
    daily quota, so skip them when you only care about the profile.

.EXAMPLE
    .\scripts\smoke-test.ps1
    .\scripts\smoke-test.ps1 -SkipFetch
    .\scripts\smoke-test.ps1 -Location pune -Keywords "senior java"
#>
[CmdletBinding()]
param(
    [string]$BaseUrl  = 'http://localhost:8080',
    [string]$Keywords = 'java developer',
    [string]$Location = 'bangalore',
    [switch]$SkipFetch,
    [int]$StartupTimeoutSeconds = 120
)

$ErrorActionPreference = 'Continue'

$RepoRoot     = Split-Path -Parent $PSScriptRoot
$DbContainer  = 'jobdiscovery-db'
$AppContainer = 'jobdiscovery-app'
$TmpDir       = Join-Path $env:TEMP 'jobdiscovery-smoke'
if (-not (Test-Path $TmpDir)) { New-Item -ItemType Directory -Path $TmpDir | Out-Null }

$script:Passed  = 0
$script:Failed  = 0
$script:Skipped = 0

# --- helpers ---------------------------------------------------------------

function Write-Section {
    param([string]$Title)
    Write-Host ''
    Write-Host "== $Title " -ForegroundColor Cyan -NoNewline
    Write-Host ('=' * [Math]::Max(0, 66 - $Title.Length)) -ForegroundColor DarkCyan
}

function Assert-That {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][bool]$Condition,
        [string]$Detail
    )
    if ($Condition) {
        $script:Passed++
        Write-Host '  [PASS] ' -ForegroundColor Green -NoNewline
        Write-Host $Name
    } else {
        $script:Failed++
        Write-Host '  [FAIL] ' -ForegroundColor Red -NoNewline
        Write-Host $Name
        if ($Detail) { Write-Host "         $Detail" -ForegroundColor DarkYellow }
    }
}

function Write-Skip {
    param([string]$Name, [string]$Why)
    $script:Skipped++
    Write-Host '  [SKIP] ' -ForegroundColor DarkGray -NoNewline
    Write-Host "$Name  ($Why)" -ForegroundColor DarkGray
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    [System.IO.File]::WriteAllText($Path, $Content, (New-Object System.Text.UTF8Encoding($false)))
}

# Calls the API with curl.exe and returns @{ Status; Body; Json }. Uses curl
# rather than Invoke-RestMethod so 4xx/5xx come back as data instead of
# exceptions - several tests assert on 404 and 400 deliberately.
function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [Parameter(Mandatory)][string]$Path,
        [string]$BodyFile
    )
    $outFile  = Join-Path $TmpDir 'response.json'
    if (Test-Path $outFile) { Remove-Item $outFile -Force }

    $curlArgs = @('-s', '-o', $outFile, '-w', '%{http_code}', '-X', $Method, "$BaseUrl$Path")
    if ($BodyFile) {
        $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$BodyFile")
    }

    $status = & curl.exe @curlArgs

    $body = ''
    if (Test-Path $outFile) { $body = Get-Content $outFile -Raw }

    $json = $null
    if ($body -and $body.Trim()) {
        try { $json = $body | ConvertFrom-Json } catch { $json = $null }
    }
    return @{ Status = [int]$status; Body = $body; Json = $json }
}

function Invoke-ApiJson {
    param([string]$Method, [string]$Path, [string]$Json)
    $f = Join-Path $TmpDir 'request.json'
    Write-Utf8NoBom -Path $f -Content $Json
    return Invoke-Api -Method $Method -Path $Path -BodyFile $f
}

# Single-value psql query against the db container.
function Invoke-Psql {
    param([Parameter(Mandatory)][string]$Sql)
    return (& docker exec $DbContainer psql -U $script:PgUser -d $script:PgDb -tAc $Sql)
}

# --- preflight -------------------------------------------------------------

Write-Host ''
Write-Host 'Intelligent Job Discovery Platform - smoke test' -ForegroundColor White
Write-Host "Target: $BaseUrl" -ForegroundColor DarkGray

Write-Section 'Preflight'

& docker version --format '{{.Server.Version}}' | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host '  [ABORT] Docker daemon is not reachable. Start Docker Desktop and retry.' -ForegroundColor Red
    exit 2
}
Write-Host '  [ OK ] Docker daemon reachable' -ForegroundColor DarkGreen

# Read POSTGRES_* out of .env so the psql checks use the right credentials.
$script:PgUser = 'jobdiscovery'
$script:PgDb   = 'jobdiscovery'
$envFile = Join-Path $RepoRoot '.env'
if (Test-Path $envFile) {
    foreach ($line in (Get-Content $envFile)) {
        if ($line -match '^\s*POSTGRES_USER\s*=\s*(.+?)\s*$') { $script:PgUser = $matches[1] }
        if ($line -match '^\s*POSTGRES_DB\s*=\s*(.+?)\s*$')   { $script:PgDb   = $matches[1] }
    }
    Write-Host "  [ OK ] .env read (db=$($script:PgDb), user=$($script:PgUser))" -ForegroundColor DarkGreen
} else {
    Write-Host '  [WARN] No .env found - falling back to default db/user names.' -ForegroundColor Yellow
}

# Wait for the app to answer health - covers a stack that is still booting.
$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$healthy  = $false
while ((Get-Date) -lt $deadline) {
    $h = Invoke-Api -Path '/actuator/health'
    if ($h.Status -eq 200) { $healthy = $true; break }
    Write-Host '  ... waiting for the app to become reachable' -ForegroundColor DarkGray
    Start-Sleep -Seconds 5
}
if (-not $healthy) {
    Write-Host "  [ABORT] $BaseUrl/actuator/health did not respond within $StartupTimeoutSeconds s." -ForegroundColor Red
    Write-Host '          Bring the stack up first:  docker compose up --build -d' -ForegroundColor DarkYellow
    exit 2
}
Write-Host '  [ OK ] App is answering' -ForegroundColor DarkGreen

# --- 1. health -------------------------------------------------------------

Write-Section '1. Stack health (Phase 0)'

$health = Invoke-Api -Path '/actuator/health'
Assert-That -Name 'GET /actuator/health returns 200' -Condition ($health.Status -eq 200) `
            -Detail "got HTTP $($health.Status)"
Assert-That -Name 'Overall status is UP' -Condition ($health.Json.status -eq 'UP') `
            -Detail "got '$($health.Json.status)'"
Assert-That -Name 'Postgres component is UP' -Condition ($health.Json.components.db.status -eq 'UP') `
            -Detail "got '$($health.Json.components.db.status)'"

# --- 2. flyway -------------------------------------------------------------

Write-Section '2. Flyway schema (Phases 1-4)'

$versions = Invoke-Psql 'select version from flyway_schema_history where success order by version;'
$applied  = @($versions | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_.Trim() })
foreach ($v in @('1', '2', '3')) {
    Assert-That -Name "Migration V$v applied successfully" -Condition ($applied -contains $v) `
                -Detail "applied versions: $($applied -join ', ')"
}
$failedMigrations = Invoke-Psql 'select count(*) from flyway_schema_history where success = false;'
Assert-That -Name 'No failed migrations' -Condition ("$failedMigrations".Trim() -eq '0') `
            -Detail "failed rows: $failedMigrations"

# --- 3-9 -------------------------------------------------------------------

$backup = $null
try {
    Write-Section '3. Candidate profile lifecycle (Phase 4)'

    # Back up whatever is there so the destructive tests below are reversible.
    $existing = Invoke-Api -Path '/api/profile'
    if ($existing.Status -eq 200) {
        $backup = $existing.Body
        Write-Host '  (existing profile backed up; it will be restored at the end)' -ForegroundColor DarkGray
    }

    $del = Invoke-Api -Method DELETE -Path '/api/profile'
    Assert-That -Name 'DELETE /api/profile returns 204' -Condition ($del.Status -eq 204) `
                -Detail "got HTTP $($del.Status)"

    $missing = Invoke-Api -Path '/api/profile'
    Assert-That -Name 'GET on an unset profile returns 404' -Condition ($missing.Status -eq 404) `
                -Detail "got HTTP $($missing.Status)"
    Assert-That -Name "404 body carries error 'profile_not_found'" `
                -Condition ($missing.Json.error -eq 'profile_not_found') `
                -Detail "got '$($missing.Json.error)'"

    $sampleFile = Join-Path $RepoRoot 'sample-profile.json'
    if (-not (Test-Path $sampleFile)) {
        Write-Skip 'Profile round-trip and upsert tests' 'sample-profile.json not found'
    } else {
        $created = Invoke-Api -Method POST -Path '/api/profile' -BodyFile $sampleFile
        Assert-That -Name 'POST /api/profile returns 200' -Condition ($created.Status -eq 200) `
                    -Detail "got HTTP $($created.Status): $($created.Body)"

        $fetched = Invoke-Api -Path '/api/profile'
        Assert-That -Name 'GET returns the saved profile (200)' -Condition ($fetched.Status -eq 200) `
                    -Detail "got HTTP $($fetched.Status)"

        # The load-bearing check: four EAGER @ElementCollection bags on one
        # entity must all come back under open-in-view=false, without tripping
        # MultipleBagFetchException.
        $p = $fetched.Json
        Assert-That -Name 'skills round-tripped'             -Condition (@($p.skills).Count -gt 0)
        Assert-That -Name 'keywords round-tripped'           -Condition (@($p.keywords).Count -gt 0)
        Assert-That -Name 'preferredLocations round-tripped' -Condition (@($p.preferredLocations).Count -gt 0)
        Assert-That -Name 'preferredCompanies round-tripped' -Condition (@($p.preferredCompanies).Count -gt 0)
        Assert-That -Name 'experienceYears round-tripped'    -Condition ($null -ne $p.experienceYears)

        Write-Section '4. Profile upsert (singleton semantics)'

        $before = $fetched.Json
        Start-Sleep -Milliseconds 50
        $again  = Invoke-Api -Method POST -Path '/api/profile' -BodyFile $sampleFile
        $after  = $again.Json
        Assert-That -Name 'Second POST reuses the same row (id preserved)' `
                    -Condition ($after.id -eq $before.id) `
                    -Detail "before=$($before.id) after=$($after.id)"
        Assert-That -Name 'createdAt is preserved' `
                    -Condition ([string]$after.createdAt -eq [string]$before.createdAt) `
                    -Detail "before=$($before.createdAt) after=$($after.createdAt)"
        Assert-That -Name 'updatedAt is bumped' `
                    -Condition ([string]$after.updatedAt -ne [string]$before.updatedAt) `
                    -Detail "both=$($after.updatedAt)"
        $rowCount = Invoke-Psql 'select count(*) from candidate_profile;'
        Assert-That -Name 'Exactly one profile row exists' -Condition ("$rowCount".Trim() -eq '1') `
                    -Detail "rows=$rowCount"
    }

    Write-Section '5. List cleaning (trim, drop blanks and duplicates)'

    $messy = Invoke-ApiJson -Method POST -Path '/api/profile' `
        -Json '{"skills":["  Java  ","Java","Kafka",""],"experienceYears":10}'
    Assert-That -Name 'POST with messy list values returns 200' -Condition ($messy.Status -eq 200) `
                -Detail "got HTTP $($messy.Status): $($messy.Body)"
    $skills = @($messy.Json.skills) -join ','
    Assert-That -Name 'Values trimmed, blanks and duplicates dropped' -Condition ($skills -eq 'Java,Kafka') `
                -Detail "expected 'Java,Kafka', got '$skills'"

    Write-Section '6. Bean validation'

    $bad1 = Invoke-ApiJson -Method POST -Path '/api/profile' `
        -Json '{"skills":[],"experienceYears":-1}'
    Assert-That -Name 'Empty skills + negative years returns 400' -Condition ($bad1.Status -eq 400) `
                -Detail "got HTTP $($bad1.Status)"
    Assert-That -Name "400 body carries error 'validation_failed'" `
                -Condition ($bad1.Json.error -eq 'validation_failed') `
                -Detail "got '$($bad1.Json.error)'"
    Assert-That -Name 'Per-field message reported for skills' `
                -Condition ($null -ne $bad1.Json.fields.skills) -Detail "body: $($bad1.Body)"
    Assert-That -Name 'Per-field message reported for experienceYears' `
                -Condition ($null -ne $bad1.Json.fields.experienceYears) -Detail "body: $($bad1.Body)"

    $bad2 = Invoke-ApiJson -Method POST -Path '/api/profile' -Json '{"skills":["Java"]}'
    Assert-That -Name 'Missing experienceYears returns 400' -Condition ($bad2.Status -eq 400) `
                -Detail "got HTTP $($bad2.Status)"

    Write-Section '7. Cascade delete'

    $del2 = Invoke-Api -Method DELETE -Path '/api/profile'
    Assert-That -Name 'DELETE returns 204' -Condition ($del2.Status -eq 204) `
                -Detail "got HTTP $($del2.Status)"
    foreach ($t in @('skills', 'keywords', 'preferred_locations', 'preferred_companies')) {
        $c = Invoke-Psql "select count(*) from candidate_profile_$t;"
        Assert-That -Name "Child table candidate_profile_$t is empty" -Condition ("$c".Trim() -eq '0') `
                    -Detail "rows=$c"
    }

    Write-Section '8. Multi-source fetch + de-duplication (Phases 1-2)'

    if ($SkipFetch) {
        Write-Skip 'Fetch tests' '-SkipFetch was passed'
    } else {
        $q = '?keywords=' + [uri]::EscapeDataString($Keywords) + '&location=' + [uri]::EscapeDataString($Location)

        $countBefore = [int]((Invoke-Api -Path '/api/jobs/count').Body)
        Write-Host "  (jobs in DB before: $countBefore)" -ForegroundColor DarkGray

        $run1 = Invoke-Api -Method POST -Path "/api/fetch$q"
        Assert-That -Name 'POST /api/fetch returns 200' -Condition ($run1.Status -eq 200) `
                    -Detail "got HTTP $($run1.Status): $($run1.Body)"
        Assert-That -Name 'First run fetched at least one listing' `
                    -Condition ($run1.Json.totalFetched -gt 0) `
                    -Detail "totalFetched=$($run1.Json.totalFetched) - check the API keys in .env"

        foreach ($s in $run1.Json.sources) {
            Assert-That -Name "Source $($s.source) returned without error" -Condition ($null -eq $s.error) `
                        -Detail $s.error
            Write-Host "         $($s.source): fetched=$($s.fetched) saved=$($s.saved) duplicates=$($s.duplicates)" -ForegroundColor DarkGray
        }

        $sourceNames = @($run1.Json.sources | ForEach-Object { $_.source })
        Assert-That -Name 'Arbeitnow is not an active source' `
                    -Condition (-not ($sourceNames -contains 'ARBEITNOW')) `
                    -Detail "active sources: $($sourceNames -join ', ')"

        $countMid = [int]((Invoke-Api -Path '/api/jobs/count').Body)
        Assert-That -Name 'Job count grew by exactly totalSaved' `
                    -Condition ($countMid -eq ($countBefore + $run1.Json.totalSaved)) `
                    -Detail "before=$countBefore saved=$($run1.Json.totalSaved) after=$countMid"

        # The de-dup claim: an immediate re-run must persist nothing new.
        $run2 = Invoke-Api -Method POST -Path "/api/fetch$q"
        Assert-That -Name 'Re-run saves 0 new jobs (content-hash dedupe)' `
                    -Condition ($run2.Json.totalSaved -eq 0) `
                    -Detail "totalSaved=$($run2.Json.totalSaved)"

        $countAfter = [int]((Invoke-Api -Path '/api/jobs/count').Body)
        Assert-That -Name 'Job count unchanged by the re-run' -Condition ($countAfter -eq $countMid) `
                    -Detail "before re-run=$countMid after=$countAfter"

        $hashDupes = Invoke-Psql 'select count(*) from (select content_hash from job_listing group by content_hash having count(*) > 1) d;'
        Assert-That -Name 'No duplicate content hashes in the table' -Condition ("$hashDupes".Trim() -eq '0') `
                    -Detail "duplicate hash groups=$hashDupes"

        $jobs = Invoke-Api -Path '/api/jobs'
        Assert-That -Name 'GET /api/jobs returns every persisted listing' `
                    -Condition ($jobs.Status -eq 200 -and @($jobs.Json).Count -eq $countAfter) `
                    -Detail "HTTP $($jobs.Status), returned $(@($jobs.Json).Count) vs count $countAfter"
    }

    Write-Section '9. Scheduler registration (Phase 3)'

    $logs       = & docker logs $AppContainer --tail 2000
    $registered = @($logs | Select-String -SimpleMatch 'Scheduled daily fetch ENABLED')
    Assert-That -Name 'Scheduler registered at boot' -Condition ($registered.Count -gt 0) `
                -Detail 'no "Scheduled daily fetch ENABLED" line in the last 2000 log lines'
    if ($registered.Count -gt 0) {
        Write-Host "         $($registered[-1].Line.Trim())" -ForegroundColor DarkGray
    }

    $fired = @($logs | Select-String -SimpleMatch 'Scheduled fetch complete')
    if ($fired.Count -gt 0) {
        Write-Host "  [ OK ] Found $($fired.Count) completed scheduled run(s) in the logs" -ForegroundColor DarkGreen
    } else {
        Write-Host '  [NOTE] No scheduled run has fired yet in these logs. To prove it fires,' -ForegroundColor DarkGray
        Write-Host '         set FETCH_SCHEDULE_CRON="0 */2 * * * *" in .env, run' -ForegroundColor DarkGray
        Write-Host '         "docker compose up -d --force-recreate app", wait ~2 min, then' -ForegroundColor DarkGray
        Write-Host '         REVERT the cron in .env and recreate again.' -ForegroundColor DarkGray
    }

    Write-Section '10. Ranked matches (Phase 5 rule engine)'

    # Section 7 left the profile deleted, so the 404 path can be checked first.
    $noProfile = Invoke-Api -Path '/api/matches'
    Assert-That -Name 'Matches without a profile returns 404' -Condition ($noProfile.Status -eq 404) `
                -Detail "got HTTP $($noProfile.Status)"

    $sampleFile = Join-Path $RepoRoot 'sample-profile.json'
    if (-not (Test-Path $sampleFile)) {
        Write-Skip 'Ranking tests' 'sample-profile.json not found'
    } else {
        Invoke-Api -Method POST -Path '/api/profile' -BodyFile $sampleFile | Out-Null

        $jobCount = [int]((Invoke-Api -Path '/api/jobs/count').Body)
        if ($jobCount -eq 0) {
            Write-Skip 'Ranking tests' 'no jobs in the database - run without -SkipFetch'
        } else {
            $matches = Invoke-Api -Path '/api/matches?limit=5'
            Assert-That -Name 'GET /api/matches returns 200' -Condition ($matches.Status -eq 200) `
                        -Detail "got HTTP $($matches.Status): $($matches.Body)"

            # ConvertFrom-Json on "[]" yields $null in PS 5.1, and @($null)
            # has Count 1 - filter the nulls out so an empty result counts as 0.
            $ranked = @($matches.Json | Where-Object { $null -ne $_ })
            Assert-That -Name 'Honours the limit' -Condition ($ranked.Count -le 5) `
                        -Detail "returned $($ranked.Count)"
            Assert-That -Name 'Returns at least one match' -Condition ($ranked.Count -gt 0) `
                        -Detail 'ranking produced nothing from a non-empty job table'

            if ($ranked.Count -gt 0) {
                $inRange = $true
                $descending = $true
                $previous = 101.0
                foreach ($m in $ranked) {
                    if ($m.score -lt 0 -or $m.score -gt 100) { $inRange = $false }
                    if ($m.score -gt $previous) { $descending = $false }
                    $previous = $m.score
                }
                Assert-That -Name 'Every score sits within 0-100' -Condition $inRange
                Assert-That -Name 'Results are ranked highest first' -Condition $descending `
                            -Detail "scores: $(($ranked | ForEach-Object { $_.score }) -join ', ')"

                $top = $ranked[0]
                Assert-That -Name 'Each match carries a six-dimension breakdown' `
                            -Condition (@($top.components).Count -eq 6) `
                            -Detail "got $(@($top.components).Count) components"
                Assert-That -Name 'Breakdown names the seniority dimension' `
                            -Condition (@($top.components | Where-Object { $_.name -eq 'seniority' }).Count -eq 1)
                Assert-That -Name 'Each match reports matched and missing skills' `
                            -Condition ($null -ne $top.matchedSkills -and $null -ne $top.missingSkills)

                Write-Host "         top match: $([Math]::Round($top.score,1)) - $($top.title) @ $($top.company) ($($top.location))" -ForegroundColor DarkGray
                Write-Host "         seniority: $($top.jobSeniority); matched skills: $(@($top.matchedSkills) -join ', ')" -ForegroundColor DarkGray
            }

            $filtered = Invoke-Api -Path '/api/matches?minScore=101'
            $filteredCount = @($filtered.Json | Where-Object { $null -ne $_ }).Count
            Assert-That -Name 'minScore filters everything out above 100' `
                        -Condition ($filteredCount -eq 0) `
                        -Detail "returned $filteredCount"
        }
    }
}
finally {
    Write-Section 'Restoring profile'

    $restoreFile  = $null
    $restoreLabel = $null
    if ($backup) {
        # POST accepts the GET shape - id/createdAt/updatedAt are ignored.
        $restoreFile  = Join-Path $TmpDir 'restore.json'
        Write-Utf8NoBom -Path $restoreFile -Content $backup
        $restoreLabel = 'the profile that was set before this run'
    } else {
        foreach ($candidate in @('my-profile.json', 'sample-profile.json')) {
            $path = Join-Path $RepoRoot $candidate
            if (Test-Path $path) {
                $restoreFile  = $path
                $restoreLabel = $candidate
                break
            }
        }
    }

    if ($restoreFile) {
        $restored = Invoke-Api -Method POST -Path '/api/profile' -BodyFile $restoreFile
        if ($restored.Status -eq 200) {
            Write-Host "  Restored $restoreLabel" -ForegroundColor DarkGreen
        } else {
            Write-Host "  [WARN] Restore failed (HTTP $($restored.Status)). Re-POST your profile manually." -ForegroundColor Yellow
        }
    } else {
        Write-Host '  [WARN] Nothing to restore from - no profile is set.' -ForegroundColor Yellow
    }
}

# --- summary ---------------------------------------------------------------

Write-Section 'Summary'

Write-Host "  Passed:  $($script:Passed)" -ForegroundColor Green
if ($script:Skipped -gt 0) { Write-Host "  Skipped: $($script:Skipped)" -ForegroundColor DarkGray }
if ($script:Failed -gt 0) {
    Write-Host "  Failed:  $($script:Failed)" -ForegroundColor Red
    Write-Host ''
    Write-Host 'SMOKE TEST FAILED' -ForegroundColor Red
    exit 1
}
Write-Host ''
Write-Host 'SMOKE TEST PASSED - Phases 0-5 verified.' -ForegroundColor Green
exit 0
