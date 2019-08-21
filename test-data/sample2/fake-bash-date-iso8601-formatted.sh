## summary : Generates a simple timestamp.
## keywords : bash, date, iso8601
## publish : github-gists
## authors : @crodav
## id : 4947ee5e-db86-494d-b2c8-3963d84490a7

date -Is   # not GMT
date -u +"%Y-%m-%dT%H:%M:%SZ"
date -u +"%Y-%m-%dT%H:%M:%S.%3NZ"
