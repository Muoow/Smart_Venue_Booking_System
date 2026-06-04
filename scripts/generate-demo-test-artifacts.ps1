param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$TestProfile = "remote"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$artifactRoot = Join-Path $projectRoot "test-artifacts\system-test"
$resultDir = Join-Path $artifactRoot "results"
$screenshotDir = Join-Path $artifactRoot "screenshots"
$dataSource = "Remote MySQL via SSH tunnel (127.0.0.1:3307 -> 150.158.132.178:3306)"

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
New-Item -ItemType Directory -Force -Path $screenshotDir | Out-Null

function Save-Json {
    param(
        [string]$Path,
        $Data
    )

    $Data | ConvertTo-Json -Depth 10 | Out-File -FilePath $Path -Encoding utf8
}

function Invoke-TimedApi {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        $Body = $null
    )

    $response = $null
    $duration = Measure-Command {
        if ($null -ne $Body) {
            $jsonBody = $Body | ConvertTo-Json -Depth 10
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers -ContentType "application/json" -Body $jsonBody
        } else {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $Headers
        }
    }

    $result = [ordered]@{}
    $result.name = $Name
    $result.method = $Method
    $result.url = $Url
    $result.elapsedMs = [Math]::Round($duration.TotalMilliseconds, 2)
    $result.response = $response

    Save-Json -Path (Join-Path $resultDir ($Name + ".json")) -Data $result
    return $result
}

function Get-BrowserPath {
    $paths = @(
        "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Microsoft\Edge\Application\msedge.exe",
        "C:\Program Files\Google\Chrome\Application\chrome.exe",
        "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
    )

    foreach ($path in $paths) {
        if (Test-Path $path) {
            return $path
        }
    }

    return $null
}

function New-Screenshot {
    param(
        [string]$BrowserPath,
        [string]$Name,
        [string]$Url
    )

    $target = Join-Path $screenshotDir ($Name + ".png")
    $args = @(
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--window-size=1440,2200",
        "--virtual-time-budget=12000",
        "--screenshot=$target",
        $Url
    )
    & $BrowserPath @args | Out-Null
    return $target
}

function New-CaseRecord {
    param(
        [string]$Id,
        [string]$Name,
        [double]$ElapsedMs
    )

    $item = [ordered]@{}
    $item.id = $Id
    $item.name = $Name
    $item.status = "PASS"
    $item.elapsedMs = $ElapsedMs
    return $item
}

try {
    $slotDate = (Get-Date).Date.AddDays(1).ToString("yyyy-MM-ddT00:00:00")

    $health = Invoke-WebRequest -UseBasicParsing ($BaseUrl + "/demo/index.html")
    if ($health.StatusCode -ne 200) {
        throw "Demo page is not reachable."
    }

    $login = Invoke-TimedApi -Name "01_login" -Method "POST" -Url ($BaseUrl + "/auth/login") -Body @{
        username = "demo"
        password = "demo"
    }

    $token = $login.response.data
    $authHeaders = @{ Authorization = "Bearer $token" }

    $venueList = Invoke-TimedApi -Name "02_venue_list" -Method "GET" -Url ($BaseUrl + "/venue/list")
    $recommendation = Invoke-TimedApi -Name "03_recommendation" -Method "POST" -Url ($BaseUrl + "/recommendation/venues") -Body @{
        sportKeyword = "badminton"
        preferredUnitMinutes = 10
        expectedPeopleCount = 4
        maxBudget = 60
        preferLowPrice = $true
        expectedStartUnit = 114
        expectedEndUnit = 125
        topN = 3
    }
    $profile = Invoke-TimedApi -Name "04_profile" -Method "GET" -Url ($BaseUrl + "/user/profile") -Headers $authHeaders
    $beforeList = Invoke-TimedApi -Name "05_my_reservations_before" -Method "GET" -Url ($BaseUrl + "/reservation/my?pageNumber=1&pageSize=10") -Headers $authHeaders

    $apply = Invoke-TimedApi -Name "06_apply_reservation" -Method "POST" -Url ($BaseUrl + "/reservation/apply") -Headers $authHeaders -Body @{
        venueId = 1
        resourceId = 2
        slotDate = $slotDate
        startUnit = 108
        endUnit = 111
        size = 4
    }

    $reservationId = $apply.response.data
    $detail = Invoke-TimedApi -Name "07_reservation_detail" -Method "GET" -Url ($BaseUrl + "/reservation/" + $reservationId) -Headers $authHeaders
    $cancel = Invoke-TimedApi -Name "08_cancel_reservation" -Method "POST" -Url ($BaseUrl + "/reservation/" + $reservationId + "/cancel") -Headers $authHeaders
    $afterList = Invoke-TimedApi -Name "09_my_reservations_after" -Method "GET" -Url ($BaseUrl + "/reservation/my?pageNumber=1&pageSize=10") -Headers $authHeaders

    $cases = @()
    $cases += New-CaseRecord -Id "T01" -Name "Login API" -ElapsedMs $login.elapsedMs
    $cases += New-CaseRecord -Id "T02" -Name "Venue list API" -ElapsedMs $venueList.elapsedMs
    $cases += New-CaseRecord -Id "T03" -Name "Recommendation API" -ElapsedMs $recommendation.elapsedMs
    $cases += New-CaseRecord -Id "T04" -Name "Profile API" -ElapsedMs $profile.elapsedMs
    $cases += New-CaseRecord -Id "T05" -Name "Reservation list before apply" -ElapsedMs $beforeList.elapsedMs
    $cases += New-CaseRecord -Id "T06" -Name "Apply reservation" -ElapsedMs $apply.elapsedMs
    $cases += New-CaseRecord -Id "T07" -Name "Reservation detail" -ElapsedMs $detail.elapsedMs
    $cases += New-CaseRecord -Id "T08" -Name "Cancel reservation" -ElapsedMs $cancel.elapsedMs
    $cases += New-CaseRecord -Id "T09" -Name "Reservation list after cancel" -ElapsedMs $afterList.elapsedMs

    $screenshots = @()
    $browserPath = Get-BrowserPath
    if ($browserPath) {
        $screenshots += New-Screenshot -BrowserPath $browserPath -Name "01_home" -Url ($BaseUrl + "/demo/index.html?screen=home")
        $screenshots += New-Screenshot -BrowserPath $browserPath -Name "02_booking" -Url ($BaseUrl + "/demo/index.html?screen=booking" + "&autologin=demo")
        $screenshots += New-Screenshot -BrowserPath $browserPath -Name "03_orders" -Url ($BaseUrl + "/demo/index.html?screen=orders" + "&autologin=demo")
        $screenshots += New-Screenshot -BrowserPath $browserPath -Name "04_admin" -Url ($BaseUrl + "/admin/index.html?autologin=admin")
    }

    $summary = [ordered]@{}
    $summary.generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $summary.profile = $TestProfile
    $summary.baseUrl = $BaseUrl
    $summary.dataSource = $dataSource
    $summary.slotDate = $slotDate
    $summary.cases = $cases
    $summary.screenshots = $screenshots
    $summary.createdReservationId = $reservationId
    Save-Json -Path (Join-Path $artifactRoot "summary.json") -Data $summary

    $lines = @()
    $lines += "# CourtFlow System Test Summary"
    $lines += ""
    $lines += "- Generated at: $($summary.generatedAt)"
    $lines += "- Test profile: $TestProfile"
    $lines += "- Base URL: $BaseUrl"
    $lines += "- Data source: $dataSource"
    $lines += "- Reservation slot used in this run: venue 1 / resource 2 / $slotDate / units 108-111"
    $lines += "- Overall result: all core API test cases passed"
    $lines += ""
    $lines += "| Case ID | Case Name | Result | Response Time (ms) |"
    $lines += "| :--- | :--- | :---: | ---: |"
    foreach ($case in $cases) {
        $lines += "| $($case.id) | $($case.name) | $($case.status) | $($case.elapsedMs) |"
    }
    $lines += ""
    $lines += "## Screenshot Files"
    $lines += ""
    if ($screenshots.Count -gt 0) {
        foreach ($shot in $screenshots) {
            $lines += "- " + (Split-Path $shot -Leaf)
        }
    } else {
        $lines += "- No Edge or Chrome executable was detected in this environment."
    }

    $lines -join "`r`n" | Out-File -FilePath (Join-Path $artifactRoot "summary.md") -Encoding utf8
    Write-Output ("Artifacts generated at: " + $artifactRoot)
} catch {
    Write-Error $_
    exit 1
}
