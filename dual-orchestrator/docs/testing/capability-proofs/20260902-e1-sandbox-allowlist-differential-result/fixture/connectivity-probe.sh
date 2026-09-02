#!/bin/sh
set +e

curl -fsS --max-time 15 https://example.com/ > allowed.out 2> allowed.err
allowed_exit=$?
curl -fsS --max-time 15 https://www.cloudflare.com/cdn-cgi/trace > denied.out 2> denied.err
denied_exit=$?

printf 'allowed_example_com\t%s\n' "$allowed_exit" > results.tsv
printf 'denied_cloudflare_com\t%s\n' "$denied_exit" >> results.tsv
exit 0
