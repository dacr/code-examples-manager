## summary : Generates a simple timestamp.
## keywords : bash, date, iso8601
## publish : github-gists
## authors : @crodav
## id : c5fde045-d89d-45de-99ff-8b0e069595d4

date -Is   # not GMT
date -u +"%Y-%m-%dT%H:%M:%SZ"
date -u +"%Y-%m-%dT%H:%M:%S.%3NZ"
