.source "T_if_eq_7.java"
.class  public Ldot/junit/opcodes/if_eq/d/T_if_eq_7;
.super  Ljava/lang/Object;


.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(ID)I
.registers 8

       if-eq v5, v6, :Label11
       const/16 v5, 1234
:Label10
       return v5
       
:Label11
       const/4 v5, 1
       goto :Label10
.end method
