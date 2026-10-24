import json
import os
from urllib.parse import urlsplit
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

api_key = os.getenv("AEMET_API_KEY")
if not api_key:
    raise SystemExit("Missing AEMET_API_KEY.")

url = (
    "https://opendata.aemet.es/opendata/api/"
    "valores/climatologicos/inventarioestaciones/estaciones/7211B"
)

request = Request(url)
request.add_unredirected_header("api_key", api_key)

try:
    with urlopen(request, timeout=30) as response:
        print("HTTP status:", response.status)
        result = json.load(response)
except HTTPError as error:
    raise SystemExit(f"HTTP error: {error.code}") from None
except (URLError, TimeoutError):
    raise SystemExit("Connection failed or timed out.") from None
except (ValueError, UnicodeError):
    raise SystemExit("Response is not valid JSON.") from None

if not isinstance(result, dict):
    raise SystemExit("Unexpected response structure.")

print("AEMET status:", result.get("estado"))
print("Response fields:", sorted(result))

if result.get("estado") != 200:
    raise SystemExit("AEMET did not report a successful request.")

if "datos" not in result:
    raise SystemExit("Missing 'datos' field in response.")
else:
    print("Datos URL:", result["datos"])

datos_url = result["datos"]

if not isinstance(datos_url, str):
    raise SystemExit("URL in 'datos' is not valid.")
try:
    parsed_url = urlsplit(datos_url)
    if (
        parsed_url.scheme != "https"
        or parsed_url.hostname != "opendata.aemet.es"
        or parsed_url.port not in (None, 443)
        or parsed_url.username is not None
        or parsed_url.password is not None
    ):
        raise ValueError("URL is not authorized or secure.")
except ValueError:
    raise SystemExit("Invalid URL format.") from None

try:
    with urlopen(parsed_url.geturl(), timeout=30) as response:
        print("HTTPS status for datos URL:", response.status)
        station_data = json.load(response)
except HTTPError as error:
        raise SystemExit(f"Download HTTP error: {error.code}") from None

except (URLError, TimeoutError):
        raise SystemExit("Download failed or timed out.") from None

except (ValueError, UnicodeError):
        raise SystemExit("Downloaded content is not valid JSON.") from None

print("Data Type:", type(station_data).__name__)

if isinstance(station_data, dict):
    print("Data fields:", sorted(station_data.keys()))

elif isinstance(station_data, list):
    print("Number of elements:", len(station_data))

    if station_data and isinstance(station_data[0], dict):
        print("First element fields:", sorted(station_data[0].keys()))

if (
    not isinstance(station_data, list)
    or len(station_data) != 1
    or not isinstance(station_data[0], dict)
):
    raise SystemExit("Expected exactly one station record.")

station = station_data[0]

if station.get("indicativo") != "7211B":
    raise SystemExit("Station identifier does not match the request.")

print("Station identifier:", station["indicativo"])
print("Station name:", station.get("nombre"))
print("Province:", station.get("provincia"))
print("Altitude:", station.get("altitud"))

if "metadatos" not in result:
    raise SystemExit("Missing 'metadatos' field in response.")
else:
    print("Metadatos:", result["metadatos"])
