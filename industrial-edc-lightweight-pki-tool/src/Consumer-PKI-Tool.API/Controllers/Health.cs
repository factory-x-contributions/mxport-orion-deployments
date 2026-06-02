using Microsoft.AspNetCore.Mvc;

namespace Consumer_PKI_Tool.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class Health : BaseController
{
    [HttpGet("")]
    [ProducesResponseType(typeof(string), 200)]
    [ProducesResponseType(401)]
    public IActionResult Get()
    {
        return Ok("OK");
    }
}