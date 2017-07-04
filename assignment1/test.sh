

rm -fr tmp
mkdir -p tmp
for file in demo.html doge.jpg; do
  echo "Trying to fetch $file..."
  curl -i -o tmp/response localhost:$@/$file 2>> tmp/stderr >> tmp/output
  
  sep_lines=$(grep -n $'^\r$' tmp/response)
  if [ $? -ne 0 ]; then 
    echo "Response a incorrect. Missing empty line or incorrect newlines."
    echo "Please re-read how the HTTP response is supposed to look like. I got..."
    cat tmp/response
    exit 1
  fi
  
  line=$(echo $sep_lines | head -n 1 | cut -d':' -f 1)
  head -n $line tmp/response > tmp/header.$file
  tail +$(($line+1)) tmp/response > tmp/$file
  
  if [ $(grep -c $'\r$' tmp/header.$file) -ne $(wc -l  tmp/header.$file | tr -s ' ' | cut -d' ' -f2) ]; then 
    echo "Response format incorrect. Probably incorrect newlines."
    echo "Please re-read how the HTTP response is supposed to look like. I got..."
    cat tmp/response
    exit 1
  fi
  
  head -n 1 tmp/header.$file | tr -s ' ' | grep "HTTP/1.[01] 200 OK" > /dev/null
  if [ $? -ne 0 ]; then 
    echo "Response status incorrect."
    echo "Please re-read how the HTTP response is supposed to look like. I got..."
    cat tmp/response
    exit 1
  fi

  diff $file tmp/$file > /dev/null
  if [ $? -ne 0 ]; then 
    echo "Failed to retrieve correct $file. The files are not the same."
    echo "You should have a look at tmp/$file and make sure this is exactly the same as $file."
    echo "You can execute \"diff -u $file tmp/$file\" to find the differences."
    echo "I got..."
    cat tmp/$file
    exit 1
  fi
  
done

echo -ne  "GET i /demo.html HTTP/1.1\r\nHost: localhost\r\n\r\n" | netcat localhost $@ > tmp/response
head -n 1 tmp/response | tr -s ' ' | grep "HTTP/1.[01] 400 " > /dev/null
if [ $? -ne 0 ]; then
   echo "Response status incorrect. Expected a 400 response."
   echo "Please re-read how the HTTP response is supposed to look like. I got..."
   cat tmp/response
   exit 1
fi



rm -fr tmp

echo "All tests passed."
echo "-----------------"
echo "Please note that these test cases are incomplete and are handed out for your convenience only."
echo "Passing the test cases does _not_ imply that you satisfy all submission criteria."
echo "During grading we will use more thorough tests."
exit 0
