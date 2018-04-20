puts -> {
  
  run_read_str = -> { `./read_pro.sh` }
  
  run_log = run_read_str[].split(/\n/)

  ast_files = run_log.select(& -> stri { stri =~ /.rb.ast/ } )
                .map(& -> stri { stri.gsub(/(.*)lib\/(.*) >>>>>>"/, '\2') } )

  run_errors = run_log.select(& -> stri { stri =~ /Exception/ } )
  
}[]
