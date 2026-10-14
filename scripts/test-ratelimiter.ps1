
# Test the rate limiter
$baseUrl = "http://localhost:8080/v1/demo"
$apiKey = "test-client-1"
$totalRequests = 15

$allowed = 0
$rateLimited = 0
$other = 0

Write-Host "Testing rate limiter at $baseUrl" -ForegroundColor Cyan
Write-Host "Sending $totalRequests requests with API key '$apiKey'..."
Write-Host ""

for ($i = 1; $i -le $totalRequests; $i++) {
    $statusCode = & curl.exe -s -o NUL -w "%{http_code}" `
        -H "X-API-Key: $apiKey" `
        $baseUrl

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Request ${i}: curl failed" -ForegroundColor Red
        $other++
        continue
    }

    switch ($statusCode) {
        "200" {
            Write-Host "Request ${i}: HTTP 200 - Allowed" -ForegroundColor Green
            $allowed++
        }
        "429" {
            Write-Host "Request ${i}: HTTP 429 - Rate limited" -ForegroundColor Yellow
            $rateLimited++
        }
        default {
            Write-Host "Request ${i}: HTTP $statusCode - Unexpected response" -ForegroundColor Red
            $other++
        }
    }
}

Write-Host "`n--- Results ---" -ForegroundColor Cyan
Write-Host "Total requests:  $totalRequests"
Write-Host "Allowed:         $allowed"
Write-Host "Rate limited:    $rateLimited"
Write-Host "Other/errors:    $other"
