{
 :database-url "postgresql://jim:123456@127.0.0.1:5432/blackberry"
 :jimw-clj-jwt-key "test-steve-token"
 :datasource-options {:auto-commit        true
                      :read-only          false
                      :connection-timeout 30000
                      :validation-timeout 5000
                      :idle-timeout       600000
                      :max-lifetime       1800000
                      :minimum-idle       10
                      :maximum-pool-size  10
                      :pool-name          "db-pool"
                      :adapter            "postgresql"
                      :username           "jim"
                      :password           "123456"
                      :database-name      "blackberry"
                      :server-name        "localhost"
                      :port-number        5432
                      :register-mbeans    false
                      :slot               "blackberry_streaming"
                      :pg-recvlogical     "/usr/local/bin/pg_recvlogical"}
 }
